package com.example.facultyattendance.domain;

import java.time.Duration;

/** Outcome of one verified scan passed to AttendanceService. */
public final class ScanResult {
    public enum Type {
        LOGIN_RECORDED, LOGOUT_RECORDED, DUPLICATE_IGNORED,
        REJECTED_IDENTITY, REJECTED_CAMPUS, REJECTED_UNKNOWN_FACULTY,
        REJECTED_INACTIVE_FACULTY, REJECTED_DEMO_NOT_ALLOWED, ERROR
    }

    private final Type type;
    private final String message;
    private final AttendanceRecord record;   // null unless a record was written or matched
    private final String facultyUid;
    private final String facultyName;
    private final Duration retryAfter;       // only for DUPLICATE_IGNORED

    public ScanResult(Type type, String message, AttendanceRecord record,
                      String facultyUid, String facultyName, Duration retryAfter) {
        this.type = type;
        this.message = message;
        this.record = record;
        this.facultyUid = facultyUid;
        this.facultyName = facultyName;
        this.retryAfter = retryAfter;
    }

    public static ScanResult of(Type type, String message) {
        return new ScanResult(type, message, null, null, null, null);
    }

    public Type type() { return type; }
    public String message() { return message; }
    public AttendanceRecord record() { return record; }
    public String facultyUid() { return facultyUid; }
    public String facultyName() { return facultyName; }
    public Duration retryAfter() { return retryAfter; }
    public boolean isSuccess() { return type == Type.LOGIN_RECORDED || type == Type.LOGOUT_RECORDED; }
    public boolean wroteRecord() { return isSuccess(); }
}
