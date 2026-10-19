package com.example.facultyattendance.data.repository;

/** Simple success/failure result with a user-facing message. */
public final class OpResult {
    public final boolean ok;
    public final String message;

    private OpResult(boolean ok, String message) { this.ok = ok; this.message = message; }

    public static OpResult ok(String message) { return new OpResult(true, message); }
    public static OpResult fail(String message) { return new OpResult(false, message); }
}
