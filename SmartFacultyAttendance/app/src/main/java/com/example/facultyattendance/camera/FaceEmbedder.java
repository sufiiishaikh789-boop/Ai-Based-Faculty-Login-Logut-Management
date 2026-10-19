package com.example.facultyattendance.camera;

/**
 * Turns an aligned face crop into an identity embedding. This is the replaceable recognition
 * module: swap the implementation without touching the database, attendance logic, or UI.
 *
 * ML Kit face DETECTION (finding a face) is NOT an implementation of this interface: detection
 * cannot tell people apart. An implementation must be a real face-RECOGNITION model.
 */
public interface FaceEmbedder {
    /** Stable id of the model. Templates are only comparable when the model id matches. */
    String modelId();

    /** Square input side length (e.g. 112). */
    int inputSize();

    /** Per-channel normalisation: (pixel - mean) / std. */
    float inputMean();
    float inputStd();

    /** True for the debug-only demo embedder, which is NOT identity verification. */
    boolean isDemo();

    boolean isReady();

    /** Human-readable status for diagnostics (e.g. "Model file missing: ..."). */
    String readinessMessage();

    /** @param rgb float array of length inputSize*inputSize*3, row-major RGB, already normalised. */
    float[] embed(float[] rgb) throws EmbeddingException;

    void close();

    class EmbeddingException extends Exception {
        private static final long serialVersionUID = 1L;

        public EmbeddingException(String message, Throwable cause) { super(message, cause); }
    }
}
