package com.example.facultyattendance.domain;

import java.time.Instant;

/** Immutable attendance session. Use the with*() methods to derive changed copies. */
public final class AttendanceRecord {
    public static final String OPEN_SESSION = "OPEN_SESSION";
    public static final String COMPLETED = "COMPLETED";

    private final long id;                 // 0 until persisted
    private final String facultyUid;
    private final String facultyName;
    private final String localDate;        // yyyy-MM-dd in the configured zone, for date queries
    private final Instant loginAt;
    private final Instant logoutAt;        // null = open session (never fabricated)
    private final AttendanceStatus status;
    private final String loginEvidence;
    private final String logoutEvidence;
    private final boolean demo;
    private final boolean corrected;

    public AttendanceRecord(long id, String facultyUid, String facultyName, String localDate,
                            Instant loginAt, Instant logoutAt, AttendanceStatus status,
                            String loginEvidence, String logoutEvidence,
                            boolean demo, boolean corrected) {
        this.id = id;
        this.facultyUid = facultyUid;
        this.facultyName = facultyName;
        this.localDate = localDate;
        this.loginAt = loginAt;
        this.logoutAt = logoutAt;
        this.status = status;
        this.loginEvidence = loginEvidence;
        this.logoutEvidence = logoutEvidence;
        this.demo = demo;
        this.corrected = corrected;
    }

    public static AttendanceRecord newLogin(String uid, String name, String localDate, Instant at,
                                            String evidence, boolean demo) {
        return new AttendanceRecord(0, uid, name, localDate, at, null,
                AttendanceStatus.LOGGED_IN, evidence, null, demo, false);
    }

    public AttendanceRecord withId(long newId) {
        return new AttendanceRecord(newId, facultyUid, facultyName, localDate, loginAt, logoutAt,
                status, loginEvidence, logoutEvidence, demo, corrected);
    }

    public AttendanceRecord withLogout(Instant at, String evidence, boolean scanDemo) {
        return new AttendanceRecord(id, facultyUid, facultyName, localDate, loginAt, at,
                AttendanceStatus.LOGGED_OUT, loginEvidence, evidence, demo || scanDemo, corrected);
    }

    /** Administrator correction of an open session. Marked so reports can show it was not a scan. */
    public AttendanceRecord withAdminLogout(Instant at) {
        return new AttendanceRecord(id, facultyUid, facultyName, localDate, loginAt, at,
                AttendanceStatus.LOGGED_OUT, loginEvidence, "ADMIN_CORRECTION", demo, true);
    }

    public long id() { return id; }
    public String facultyUid() { return facultyUid; }
    public String facultyName() { return facultyName; }
    public String localDate() { return localDate; }
    public Instant loginAt() { return loginAt; }
    public Instant logoutAt() { return logoutAt; }
    public AttendanceStatus status() { return status; }
    public String loginEvidence() { return loginEvidence; }
    public String logoutEvidence() { return logoutEvidence; }
    public boolean isDemo() { return demo; }
    public boolean isCorrected() { return corrected; }

    public boolean isOpen() { return status == AttendanceStatus.LOGGED_IN && logoutAt == null; }

    /** Report label: OPEN_SESSION when there is no logout, otherwise COMPLETED. */
    public String sessionState() { return isOpen() ? OPEN_SESSION : COMPLETED; }
}
