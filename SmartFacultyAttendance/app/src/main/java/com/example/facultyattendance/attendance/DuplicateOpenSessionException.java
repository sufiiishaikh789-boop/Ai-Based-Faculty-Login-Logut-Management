package com.example.facultyattendance.attendance;

/** Thrown by a store when a second open session for the same faculty member would be created. */
public class DuplicateOpenSessionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DuplicateOpenSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
