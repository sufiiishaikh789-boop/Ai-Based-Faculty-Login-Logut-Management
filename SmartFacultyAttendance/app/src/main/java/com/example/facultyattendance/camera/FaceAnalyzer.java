package com.example.facultyattendance.camera;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.facultyattendance.config.RecognitionPolicy;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-frame pipeline: detect -> quality gate -> align -> embed -> (enroll sample | match + blink).
 *
 * - ML Kit only DETECTS the face and gives landmarks / eye-open probabilities. The identity decision
 *   comes from the FaceEmbedder model and FaceMatcher.
 * - Frames are throttled (MIN_INTERVAL_MS) and the analyzer runs on one background executor, so
 *   recognition never runs on every rendered frame and never on the main thread.
 * - `finished` latches exactly once: after a verified/complete callback no further frames are
 *   processed, so consecutive frames cannot trigger repeated attendance writes.
 * - The ImageProxy is always closed in a finally block.
 */
public final class FaceAnalyzer implements ImageAnalysis.Analyzer {
    public enum Mode { VERIFY, ENROLL }

    public interface Listener {
        void onState(FaceScanState state);
        void onVerified(String facultyUid, float similarity);
        void onEnrollmentProgress(int collected, int needed);
        void onEnrollmentComplete(float[] template);
        void onFatal(String message);
    }

    private static final String TAG = "FaceAnalyzer";
    private static final long MIN_INTERVAL_MS = 250;
    private static final float MIN_FACE_WIDTH_FRACTION = 0.30f;
    private static final float MAX_HEAD_ANGLE_DEG = 20f;
    private static final float MIN_LUMINANCE = 55f;
    private static final float MAX_LUMINANCE = 205f;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    private final FaceEmbedder embedder;
    private final RecognitionPolicy policy;
    private final Mode mode;
    private final List<FaceMatcher.Candidate> candidates;
    private final FaceDetector detector;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final FrameStabilizer stabilizer;
    private final LivenessTracker liveness = new LivenessTracker(8000);
    private final EnrollmentCollector collector;

    private volatile Listener listener;
    private long lastFrameAt;
    private int consecutiveErrors;
    private FaceScanState lastState;

    public FaceAnalyzer(FaceEmbedder embedder, RecognitionPolicy policy, Mode mode,
                        List<FaceMatcher.Candidate> candidates, Listener listener) {
        this.embedder = embedder;
        this.policy = policy;
        this.mode = mode;
        this.candidates = candidates;
        this.listener = listener;
        this.stabilizer = new FrameStabilizer(policy.consecutiveMatches());
        this.collector = new EnrollmentCollector(policy.enrollmentSamples());
        this.detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.25f)
                .build());
    }

    /** Stops all further work and drops the listener (call from onDestroy / on retry). */
    public void cancel() {
        finished.set(true);
        listener = null;
        main.removeCallbacksAndMessages(null);
        try { detector.close(); } catch (RuntimeException ignored) { }
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            long now = SystemClock.elapsedRealtime();
            if (finished.get() || now - lastFrameAt < MIN_INTERVAL_MS) return;
            lastFrameAt = now;
            Bitmap upright = toUpright(image);
            try {
                processFrame(upright, now);
                consecutiveErrors = 0;
            } finally {
                upright.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "frame skipped: " + e.getClass().getSimpleName());   // no image data or embeddings are logged
            if (++consecutiveErrors >= MAX_CONSECUTIVE_ERRORS && finished.compareAndSet(false, true)) {
                postFatal("The camera analysis keeps failing. Please retry.");
            }
        } finally {
            image.close();
        }
    }

    private Bitmap toUpright(ImageProxy image) {
        Bitmap raw = image.toBitmap();                       // analysis output format is RGBA_8888
        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation == 0) return raw;
        Matrix m = new Matrix();
        m.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
        if (rotated != raw) raw.recycle();
        return rotated;
    }

    private void processFrame(Bitmap bmp, long now) throws Exception {
        List<Face> faces = Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)));
        if (finished.get()) return;

        if (faces.isEmpty()) { resetTracking(); postState(FaceScanState.NO_FACE); return; }
        if (faces.size() > 1) { resetTracking(); postState(FaceScanState.MULTIPLE_FACES); return; }

        Face face = faces.get(0);
        if (!goodPose(face, bmp)) { stabilizer.reset(); postState(FaceScanState.LOW_QUALITY); return; }

        Bitmap crop = FaceCropper.alignedCrop(bmp, face, embedder.inputSize());
        float lum = FaceCropper.meanLuminance(crop);
        if (lum < MIN_LUMINANCE || lum > MAX_LUMINANCE) {
            crop.recycle();
            stabilizer.reset();
            postState(FaceScanState.LOW_QUALITY);
            return;
        }
        float[] rgb = FaceCropper.toNormalizedRgb(crop, embedder.inputMean(), embedder.inputStd());
        crop.recycle();

        float[] embedding;
        try {
            embedding = FaceMatcher.l2Normalize(embedder.embed(rgb));
        } catch (FaceEmbedder.EmbeddingException e) {
            throw new IllegalStateException("embedding failed");
        }

        if (mode == Mode.ENROLL) handleEnroll(embedding); else handleVerify(face, embedding, now);
    }

    private boolean goodPose(Face face, Bitmap bmp) {
        if (Math.abs(face.getHeadEulerAngleY()) > MAX_HEAD_ANGLE_DEG
                || Math.abs(face.getHeadEulerAngleX()) > MAX_HEAD_ANGLE_DEG) return false;
        float minSide = Math.min(bmp.getWidth(), bmp.getHeight());
        if (face.getBoundingBox().width() < minSide * MIN_FACE_WIDTH_FRACTION) return false;
        float cx = face.getBoundingBox().exactCenterX() / bmp.getWidth();
        float cy = face.getBoundingBox().exactCenterY() / bmp.getHeight();
        return cx > 0.2f && cx < 0.8f && cy > 0.2f && cy < 0.8f;
    }

    private void handleEnroll(float[] embedding) {
        collector.add(embedding);
        postProgress(collector.collected(), collector.needed());
        postState(FaceScanState.ENROLLING);
        if (!collector.isComplete()) return;
        if (!finished.compareAndSet(false, true)) return;
        try {
            final float[] template = collector.buildTemplate();
            final Listener l = listener;
            if (l != null) main.post(() -> { Listener cur = listener; if (cur != null) cur.onEnrollmentComplete(template); });
        } catch (IllegalStateException inconsistent) {
            finished.set(false);          // let the user try again with fresh samples
            collector.clear();
            postProgress(0, collector.needed());
            postState(FaceScanState.LOW_QUALITY);
        }
    }

    private void handleVerify(Face face, float[] embedding, long now) {
        Float l = face.getLeftEyeOpenProbability();
        Float r = face.getRightEyeOpenProbability();
        liveness.update(now, l == null ? -1f : l, r == null ? -1f : r);

        FaceMatcher.MatchResult m = FaceMatcher.match(embedding, candidates,
                policy.similarityThreshold(), policy.ambiguityMargin());
        switch (m.status) {
            case NO_TEMPLATES:
                postState(FaceScanState.NO_ENROLLED_FACES);
                return;
            case NO_MATCH:
                stabilizer.reset();
                postState(FaceScanState.MISMATCH);
                return;
            case AMBIGUOUS:
                stabilizer.reset();
                postState(FaceScanState.AMBIGUOUS);
                return;
            default:
                break;
        }
        boolean stable = stabilizer.offer(m.uid);
        if (policy.requireBlink() && !liveness.isPassed()) { postState(FaceScanState.BLINK_REQUIRED); return; }
        if (!stable) { postState(FaceScanState.VERIFYING); return; }
        if (!finished.compareAndSet(false, true)) return;          // exactly one verified callback, ever
        final String uid = m.uid;
        final float sim = m.best;
        main.post(() -> { Listener cur = listener; if (cur != null) cur.onVerified(uid, sim); });
    }

    private void resetTracking() {
        stabilizer.reset();
        liveness.reset();
    }

    private void postState(final FaceScanState s) {
        if (s == lastState) return;                // avoid flooding the UI with identical states
        lastState = s;
        main.post(() -> { Listener cur = listener; if (cur != null) cur.onState(s); });
    }

    private void postProgress(final int c, final int n) {
        main.post(() -> { Listener cur = listener; if (cur != null) cur.onEnrollmentProgress(c, n); });
    }

    private void postFatal(final String msg) {
        main.post(() -> { Listener cur = listener; if (cur != null) cur.onFatal(msg); });
    }
}
