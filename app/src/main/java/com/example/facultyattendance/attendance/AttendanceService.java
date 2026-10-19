package com.example.facultyattendance.attendance;

import com.example.facultyattendance.config.AttendancePolicy;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.EligibilityResult;
import com.example.facultyattendance.domain.FacultyRecord;
import com.example.facultyattendance.domain.IdentityResult;
import com.example.facultyattendance.domain.ScanResult;
import com.example.facultyattendance.domain.ScanResult.Type;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * The attendance state machine.
 *
 *  no open session                      -> create LOGGED_IN
 *  open session, scan inside window     -> ignored (duplicate), nothing is written
 *  open session, scan after the window  -> LOGGED_OUT with the logout instant
 *  scan shortly after a logout          -> ignored (re-login cooldown), later scans open a new session
 *
 * The window is measured from the LOGIN instant. Nothing is written unless BOTH identity and
 * campus eligibility are positive, and everything happens in one store transaction.
 */
public final class AttendanceService {
    private static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofSeconds(5);

    private final AttendanceStore store;
    private final Clock clock;
    private final AttendancePolicy policy;
    private final boolean allowDemoRecords;
    private final Consumer<Throwable> errorReporter;

    public AttendanceService(AttendanceStore store, Clock clock, AttendancePolicy policy,
                             boolean allowDemoRecords, Consumer<Throwable> errorReporter) {
        this.store = store;
        this.clock = clock;
        this.policy = policy;
        this.allowDemoRecords = allowDemoRecords;
        this.errorReporter = errorReporter == null ? new Consumer<Throwable>() {
            @Override public void accept(Throwable t) { }
        } : errorReporter;
    }

    public ScanResult recordScan(final IdentityResult identity, final EligibilityResult eligibility) {
        if (identity == null || !identity.isVerified()) {
            return ScanResult.of(Type.REJECTED_IDENTITY,
                    "Identity could not be verified. No attendance was recorded.");
        }
        if (eligibility == null || !eligibility.isEligible()) {
            String m = eligibility == null ? "Campus check missing." : eligibility.message();
            return ScanResult.of(Type.REJECTED_CAMPUS, m + " No attendance was recorded.");
        }
        final boolean demo = identity.isDemo() || eligibility.isDemo();
        if (demo && !allowDemoRecords) {
            return ScanResult.of(Type.REJECTED_DEMO_NOT_ALLOWED,
                    "Demo verification cannot create real attendance. No attendance was recorded.");
        }
        Duration age = Duration.between(eligibility.checkedAt(), clock.instant());
        if (age.compareTo(policy.maxEligibilityAge()) > 0
                || age.negated().compareTo(CLOCK_SKEW_TOLERANCE) > 0) {
            return ScanResult.of(Type.REJECTED_CAMPUS,
                    "The campus check has expired. Please scan again. No attendance was recorded.");
        }

        final String uid = identity.facultyUid();
        try {
            return store.inTransaction(new java.util.concurrent.Callable<ScanResult>() {
                @Override public ScanResult call() {
                    return applyTransition(uid, eligibility, demo);
                }
            });
        } catch (DuplicateOpenSessionException e) {
            return ScanResult.of(Type.DUPLICATE_IGNORED, "A session is already open. Scan ignored.");
        } catch (RuntimeException e) {
            errorReporter.accept(e);
            return ScanResult.of(Type.ERROR,
                    "Attendance could not be saved because of a database problem. Please try again.");
        }
    }

    private ScanResult applyTransition(String uid, EligibilityResult eligibility, boolean demo) {
        FacultyRecord faculty = store.findFaculty(uid);
        if (faculty == null) {
            return ScanResult.of(Type.REJECTED_UNKNOWN_FACULTY, "This faculty ID is not enrolled.");
        }
        if (!faculty.isActive()) {
            return ScanResult.of(Type.REJECTED_INACTIVE_FACULTY, "This faculty profile is deactivated.");
        }

        Instant now = clock.instant();
        AttendanceRecord open = store.findOpenSession(uid);

        if (open == null) {
            AttendanceRecord last = store.findLatestClosedSession(uid);
            if (last != null && last.logoutAt() != null && !policy.reLoginCooldown().isZero()) {
                Duration since = Duration.between(last.logoutAt(), now);
                if (since.isNegative() || since.compareTo(policy.reLoginCooldown()) < 0) {
                    Duration wait = since.isNegative() ? policy.reLoginCooldown()
                            : policy.reLoginCooldown().minus(since);
                    return new ScanResult(Type.DUPLICATE_IGNORED,
                            "You just logged out. Scan ignored.", last, uid, faculty.name(), wait);
                }
            }
            String localDate = now.atZone(policy.zone()).toLocalDate().toString();
            AttendanceRecord saved = store.insert(AttendanceRecord.newLogin(
                    uid, faculty.name(), localDate, now, eligibility.evidence(), demo));
            return new ScanResult(Type.LOGIN_RECORDED, "Login recorded.", saved, uid, faculty.name(), null);
        }

        Duration elapsed = Duration.between(open.loginAt(), now);
        if (elapsed.isNegative() || elapsed.compareTo(policy.duplicateWindow()) < 0) {
            Duration remaining = elapsed.isNegative() ? policy.duplicateWindow()
                    : policy.duplicateWindow().minus(elapsed);
            return new ScanResult(Type.DUPLICATE_IGNORED,
                    "Already logged in. Duplicate scan ignored.", open, uid, faculty.name(), remaining);
        }

        AttendanceRecord closed = open.withLogout(now, eligibility.evidence(), demo);
        store.update(closed);
        return new ScanResult(Type.LOGOUT_RECORDED, "Logout recorded.", closed, uid, faculty.name(), null);
    }
}
