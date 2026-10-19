package com.example.facultyattendance.authentication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** In-memory administrator session with an idle timeout. Never persisted. */
public final class AdminSession {
    private final Clock clock;
    private final Duration idleTimeout;
    private Instant lastActivity;   // null = signed out

    public AdminSession(Clock clock, Duration idleTimeout) {
        this.clock = clock;
        this.idleTimeout = idleTimeout;
    }

    public synchronized void signIn() { lastActivity = clock.instant(); }

    public synchronized void signOut() { lastActivity = null; }

    /** Returns true and refreshes the idle timer when the session is still valid. */
    public synchronized boolean touchIfActive() {
        if (lastActivity == null) return false;
        Instant now = clock.instant();
        if (Duration.between(lastActivity, now).compareTo(idleTimeout) > 0) {
            lastActivity = null;
            return false;
        }
        lastActivity = now;
        return true;
    }
}
