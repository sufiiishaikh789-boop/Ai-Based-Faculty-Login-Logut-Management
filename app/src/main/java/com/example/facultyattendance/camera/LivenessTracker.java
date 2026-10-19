package com.example.facultyattendance.camera;

/**
 * BASIC blink challenge: eyes open -> closed -> open inside a time window.
 *
 * This only raises the bar against a held-up printed photo. It does NOT defeat video replay,
 * masks, or deepfakes and is not independently validated anti-spoofing (ISO/IEC 30107-3 PAD).
 */
public final class LivenessTracker {
    private static final float OPEN = 0.6f;
    private static final float CLOSED = 0.3f;

    private enum Phase { WAIT_OPEN, SAW_OPEN, SAW_CLOSED, PASSED }

    private final long windowMillis;
    private Phase phase = Phase.WAIT_OPEN;
    private long startedAt;

    public LivenessTracker(long windowMillis) { this.windowMillis = windowMillis; }

    public void reset() { phase = Phase.WAIT_OPEN; }

    public boolean isPassed() { return phase == Phase.PASSED; }

    /** @param left/right eye-open probabilities, or a negative value when unknown. */
    public boolean update(long nowMillis, float left, float right) {
        if (phase == Phase.PASSED) return true;
        if (left < 0 || right < 0) return false;            // unknown: do not advance
        float eyes = Math.min(left, right);
        if (phase != Phase.WAIT_OPEN && nowMillis - startedAt > windowMillis) phase = Phase.WAIT_OPEN;
        switch (phase) {
            case WAIT_OPEN:
                if (eyes >= OPEN) { phase = Phase.SAW_OPEN; startedAt = nowMillis; }
                break;
            case SAW_OPEN:
                if (eyes <= CLOSED) phase = Phase.SAW_CLOSED;
                break;
            case SAW_CLOSED:
                if (eyes >= OPEN) phase = Phase.PASSED;
                break;
            default:
                break;
        }
        return phase == Phase.PASSED;
    }
}
