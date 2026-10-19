package com.example.facultyattendance.attendance;

import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.AuditEntry;
import com.example.facultyattendance.domain.FacultyRecord;

import java.util.concurrent.Callable;

/**
 * Persistence port used by the attendance services. The Room implementation lives in
 * data/repository; tests use an in-memory fake. inTransaction() must run the work atomically and
 * serialise concurrent callers (Room's runInTransaction does).
 */
public interface AttendanceStore {
    <T> T inTransaction(Callable<T> work);

    FacultyRecord findFaculty(String uid);

    /** The single open session for this faculty member, or null. */
    AttendanceRecord findOpenSession(String uid);

    /** Most recent completed session (by logout time), or null. */
    AttendanceRecord findLatestClosedSession(String uid);

    AttendanceRecord findById(long id);

    /** Inserts and returns the record with its generated id. May throw DuplicateOpenSessionException. */
    AttendanceRecord insert(AttendanceRecord record);

    void update(AttendanceRecord record);

    void insertAudit(AuditEntry entry);
}
