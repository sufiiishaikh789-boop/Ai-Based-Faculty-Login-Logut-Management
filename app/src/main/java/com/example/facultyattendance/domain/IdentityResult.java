package com.example.facultyattendance.domain;

/** Result of face verification. Only VERIFIED can ever lead to an attendance write. */
public final class IdentityResult {
    public enum Outcome {
        VERIFIED, NO_FACE, MULTIPLE_FACES, MISMATCH, AMBIGUOUS, LOW_QUALITY,
        LIVENESS_FAILED, MODEL_NOT_READY, NO_ENROLLED_FACES, ERROR
    }

    private final Outcome outcome;
    private final String facultyUid;
    private final float similarity;
    private final String providerId;
    private final boolean demo;

    private IdentityResult(Outcome outcome, String facultyUid, float similarity,
                           String providerId, boolean demo) {
        this.outcome = outcome;
        this.facultyUid = facultyUid;
        this.similarity = similarity;
        this.providerId = providerId;
        this.demo = demo;
    }

    public static IdentityResult verified(String uid, float similarity, String providerId, boolean demo) {
        if (uid == null || uid.isEmpty()) throw new IllegalArgumentException("uid required");
        return new IdentityResult(Outcome.VERIFIED, uid, similarity, providerId, demo);
    }

    public static IdentityResult failed(Outcome outcome, String providerId, boolean demo) {
        if (outcome == Outcome.VERIFIED) throw new IllegalArgumentException("use verified()");
        return new IdentityResult(outcome, null, 0f, providerId, demo);
    }

    public Outcome outcome() { return outcome; }
    public String facultyUid() { return facultyUid; }
    public float similarity() { return similarity; }
    public String providerId() { return providerId; }
    public boolean isDemo() { return demo; }
    public boolean isVerified() {
        return outcome == Outcome.VERIFIED && facultyUid != null && !facultyUid.isEmpty();
    }
}
