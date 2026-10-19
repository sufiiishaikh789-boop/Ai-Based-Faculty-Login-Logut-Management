package com.example.facultyattendance.domain;

/** Persisted state of an attendance record. An open session is LOGGED_IN with a null logout instant. */
public enum AttendanceStatus {
    LOGGED_IN,
    LOGGED_OUT
}
