package com.example.facultyattendance.attendance;

import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.AuditEntry;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Callable;

/**
 * Administrator-only correction of an incomplete (open) session. The logout instant is chosen by
 * the administrator, never invented by the app, and every correction needs a written reason and
 * produces an audit entry in the same transaction.
 */
public final class AdminCorrectionService {
    public static final String ACTION_CLOSE_OPEN_SESSION = "CLOSE_OPEN_SESSION";

    public static final class Result {
        public final boolean ok;
        public final String message;
        private Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    private final AttendanceStore store;
    private final Clock clock;

    public AdminCorrectionService(AttendanceStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Result closeOpenSession(final long recordId, final Instant logoutAt,
                                   final String reason, final String actor) {
        final String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.length() < 5) return new Result(false, "A reason of at least 5 characters is required.");
        if (cleanReason.length() > 500) return new Result(false, "Reason is too long (max 500 characters).");
        if (actor == null || actor.trim().isEmpty()) return new Result(false, "Administrator identity missing.");
        if (logoutAt == null) return new Result(false, "Logout time is required.");
        try {
            return store.inTransaction(new Callable<Result>() {
                @Override public Result call() {
                    Instant now = clock.instant();
                    AttendanceRecord rec = store.findById(recordId);
                    if (rec == null) return new Result(false, "Record not found.");
                    if (!rec.isOpen()) return new Result(false, "Only open sessions can be corrected.");
                    if (logoutAt.isBefore(rec.loginAt())) return new Result(false, "Logout cannot be before login.");
                    if (logoutAt.isAfter(now)) return new Result(false, "Logout cannot be in the future.");
                    store.update(rec.withAdminLogout(logoutAt));
                    store.insertAudit(new AuditEntry(0, ACTION_CLOSE_OPEN_SESSION, recordId,
                            rec.facultyUid(), now, actor.trim(), cleanReason,
                            "previous=OPEN_SESSION; logout_set_to=" + logoutAt));
                    return new Result(true, "Session closed and audited.");
                }
            });
        } catch (RuntimeException e) {
            return new Result(false, "Database error. Nothing was changed.");
        }
    }
}
