package com.example.facultyattendance.authentication;

import com.example.facultyattendance.config.SettingsRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

/**
 * Administrator access, completely separate from face-based faculty attendance.
 * There is NO default password: the first administrator chooses one during setup.
 * Only a salted PBKDF2 hash is stored. Guessing is throttled and persisted across restarts.
 */
public final class AdminAuthManager {
    public enum LoginOutcome { SUCCESS, WRONG_PASSWORD, LOCKED, NOT_SET_UP }

    public static final class LoginResult {
        public final LoginOutcome outcome;
        public final long lockRemainingMillis;
        LoginResult(LoginOutcome o, long l) { outcome = o; lockRemainingMillis = l; }
    }

    public static final String ACTOR = "administrator";

    private final SettingsRepository settings;
    private final Clock clock;
    private final AdminSession session;

    public AdminAuthManager(SettingsRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
        this.session = new AdminSession(clock, Duration.ofMinutes(5));
    }

    public boolean isSetUp() { return settings.getAdminHash() != null; }

    /** @return false when the password does not meet policy or an administrator already exists. */
    public boolean setUp(char[] password) {
        if (isSetUp() || !PasswordHasher.isAcceptable(password)) return false;
        settings.setAdminHash(PasswordHasher.hash(password));
        Arrays.fill(password, '\0');
        session.signIn();
        return true;
    }

    public LoginResult login(char[] password) {
        String hash = settings.getAdminHash();
        if (hash == null) return new LoginResult(LoginOutcome.NOT_SET_UP, 0);
        long now = clock.millis();
        LoginThrottle throttle = new LoginThrottle(settings.getAdminFailures(), settings.getAdminLockedUntil());
        long locked = throttle.remainingLockMillis(now);
        if (locked > 0) return new LoginResult(LoginOutcome.LOCKED, locked);
        boolean ok = PasswordHasher.verify(password, hash);
        Arrays.fill(password, '\0');
        if (ok) {
            throttle.recordSuccess();
            settings.saveAdminThrottle(0, 0);
            session.signIn();
            return new LoginResult(LoginOutcome.SUCCESS, 0);
        }
        throttle.recordFailure(now);
        settings.saveAdminThrottle(throttle.failures(), throttle.lockedUntilMillis());
        long remaining = throttle.remainingLockMillis(now);
        return new LoginResult(remaining > 0 ? LoginOutcome.LOCKED : LoginOutcome.WRONG_PASSWORD, remaining);
    }

    /** True while signed in; refreshes the 5-minute idle timer. */
    public boolean isSessionActive() { return session.touchIfActive(); }

    public void logout() { session.signOut(); }
}
