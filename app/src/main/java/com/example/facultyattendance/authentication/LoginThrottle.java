package com.example.facultyattendance.authentication;

/**
 * Slows down password guessing: after MAX_FREE_FAILURES consecutive failures the account is locked
 * for 30 s, doubling per further failure up to 15 minutes. State is exposed so the caller can persist it.
 */
public final class LoginThrottle {
    public static final int MAX_FREE_FAILURES = 5;
    private static final long BASE_LOCK_MS = 30_000L;
    private static final long MAX_LOCK_MS = 15 * 60_000L;

    private int failures;
    private long lockedUntilMillis;

    public LoginThrottle(int failures, long lockedUntilMillis) {
        this.failures = Math.max(0, failures);
        this.lockedUntilMillis = lockedUntilMillis;
    }

    public int failures() { return failures; }
    public long lockedUntilMillis() { return lockedUntilMillis; }

    public long remainingLockMillis(long nowMillis) {
        return Math.max(0, lockedUntilMillis - nowMillis);
    }

    public void recordFailure(long nowMillis) {
        failures++;
        if (failures >= MAX_FREE_FAILURES) {
            int over = Math.min(failures - MAX_FREE_FAILURES, 20);
            long lock = Math.min(MAX_LOCK_MS, BASE_LOCK_MS << over);
            lockedUntilMillis = nowMillis + lock;
        }
    }

    public void recordSuccess() {
        failures = 0;
        lockedUntilMillis = 0;
    }
}
