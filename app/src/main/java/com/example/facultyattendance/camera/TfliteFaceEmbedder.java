package com.example.facultyattendance.camera;

import android.content.Context;

import com.google.ai.edge.litert.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;

/**
 * Real face-recognition embedder backed by a LiteRT (TensorFlow Lite) model that YOU supply as
 * app/src/main/assets/models/face_embedding.tflite. No model is bundled: none could be downloaded
 * or licence-checked while this project was authored. See docs/MODEL_SETUP.md for the contract
 * (input 1xHxWx3 float32 with square H=W, output 1xN float32 embedding) and the licence checklist.
 *
 * Until the file exists, isReady() is false and attendance cannot be verified: the app does not
 * pretend to recognise anyone.
 *
 * NOTE: the Interpreter class import (com.google.ai.edge.litert.Interpreter, LiteRT v1.x) could not
 * be compile-checked offline. If your IDE reports it unresolved, check the LiteRT 1.4.x
 * release notes: this is the only file that touches that API.
 */
public final class TfliteFaceEmbedder implements FaceEmbedder {
    public static final String ASSET_PATH = "models/face_embedding.tflite";

    private final Context appContext;
    private boolean attempted;
    private boolean ready;
    private String message = "Model not loaded yet.";
    private Interpreter interpreter;
    private int inputSize = 112;
    private int outputSize;
    private String modelId = "face_embedding-unavailable";
    private ByteBuffer inputBuffer;

    public TfliteFaceEmbedder(Context context) { this.appContext = context.getApplicationContext(); }

    /** Loads the model once. Blocking file IO: call from a background thread. */
    public synchronized void prepare() {
        if (attempted) return;
        attempted = true;
        try {
            byte[] bytes = readAsset();
            ByteBuffer model = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            model.put(bytes).rewind();
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            Interpreter it = new Interpreter(model, options);

            int[] in = it.getInputTensor(0).shape();
            int[] out = it.getOutputTensor(0).shape();
            if (in.length != 4 || in[0] != 1 || in[3] != 3 || in[1] != in[2]) {
                it.close();
                message = "Unsupported model input shape (expected 1xHxWx3 with H=W).";
                return;
            }
            if (out.length != 2 || out[0] != 1 || out[1] < 64) {
                it.close();
                message = "Unsupported model output shape (expected 1xN embedding, N>=64).";
                return;
            }
            interpreter = it;
            inputSize = in[1];
            outputSize = out[1];
            modelId = "face_embedding-" + sha256Hex(bytes).substring(0, 12);
            inputBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder());
            ready = true;
            message = "Model loaded (" + inputSize + "x" + inputSize + " input, " + outputSize + "-d embedding, id " + modelId + ").";
        } catch (FileNotFoundException e) {
            message = "Model file missing: assets/" + ASSET_PATH + " (see docs/MODEL_SETUP.md).";
        } catch (Exception | LinkageError e) {
            message = "Model could not be loaded (" + e.getClass().getSimpleName() + ").";
        }
    }

    /** AssetManager.open throws FileNotFoundException when the asset does not exist. */
    private byte[] readAsset() throws IOException {
        InputStream in = appContext.getAssets().open(ASSET_PATH);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    private static String sha256Hex(byte[] data) throws java.security.NoSuchAlgorithmException {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Override public synchronized String modelId() { return modelId; }
    @Override public synchronized int inputSize() { return inputSize; }
    @Override public float inputMean() { return 127.5f; }   // common MobileFaceNet/ArcFace convention; check your model
    @Override public float inputStd() { return 128.0f; }
    @Override public boolean isDemo() { return false; }
    @Override public synchronized boolean isReady() { return ready; }
    @Override public synchronized String readinessMessage() { return message; }

    @Override
    public synchronized float[] embed(float[] rgb) throws EmbeddingException {
        if (!ready) throw new EmbeddingException("model not ready", null);
        try {
            inputBuffer.rewind();
            for (float f : rgb) inputBuffer.putFloat(f);
            inputBuffer.rewind();
            float[][] out = new float[1][outputSize];
            interpreter.run(inputBuffer, out);
            return out[0];
        } catch (RuntimeException e) {
            throw new EmbeddingException("inference failed", e);
        }
    }

    @Override
    public synchronized void close() {
        if (interpreter != null) { interpreter.close(); interpreter = null; }
        ready = false;
        attempted = false;
    }
}
