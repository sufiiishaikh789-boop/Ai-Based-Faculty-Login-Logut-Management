package com.example.facultyattendance.data.repository;

import android.database.sqlite.SQLiteConstraintException;

import com.example.facultyattendance.attendance.AttendanceStore;
import com.example.facultyattendance.attendance.DuplicateOpenSessionException;
import com.example.facultyattendance.data.local.AppDatabase;
import com.example.facultyattendance.data.local.AttendanceDao;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.AuditEntry;
import com.example.facultyattendance.domain.FacultyRecord;

import java.util.concurrent.Callable;

/**
 * Room implementation of the AttendanceStore port. Room's runInTransaction() is atomic and
 * serialises writers, and the unique (facultyUid, openMarker) index is the database-level
 * backstop that makes two open sessions for one person impossible.
 */
public final class RoomAttendanceStore implements AttendanceStore {
    private final AppDatabase db;

    public RoomAttendanceStore(AppDatabase db) { this.db = db; }

    @Override public <T> T inTransaction(Callable<T> work) { return db.runInTransaction(work); }

    @Override public FacultyRecord findFaculty(String uid) {
        return Mappers.toDomain(db.facultyDao().get(uid));
    }

    @Override public AttendanceRecord findOpenSession(String uid) {
        return Mappers.toDomain(db.attendanceDao().findOpen(uid));
    }

    @Override public AttendanceRecord findLatestClosedSession(String uid) {
        return Mappers.toDomain(db.attendanceDao().findLatestClosed(uid));
    }

    @Override public AttendanceRecord findById(long id) {
        return Mappers.toDomain(db.attendanceDao().get(id));
    }

    @Override public AttendanceRecord insert(AttendanceRecord record) {
        AttendanceDao dao = db.attendanceDao();
        try {
            long id = dao.insert(Mappers.toEntity(record));
            return record.withId(id);
        } catch (SQLiteConstraintException e) {
            if (record.isOpen() && dao.findOpen(record.facultyUid()) != null) {
                throw new DuplicateOpenSessionException("open session already exists", e);
            }
            throw e;
        }
    }

    @Override public void update(AttendanceRecord record) {
        db.attendanceDao().update(Mappers.toEntity(record));
    }

    @Override public void insertAudit(AuditEntry entry) {
        db.auditDao().insert(Mappers.toEntity(entry));
    }
}
