package com.example.facultyattendance;

import com.example.facultyattendance.attendance.AttendanceStore;
import com.example.facultyattendance.attendance.DuplicateOpenSessionException;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.AuditEntry;
import com.example.facultyattendance.domain.FacultyRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/** In-memory store. The lock imitates SQLite/Room serialising write transactions. */
class FakeAttendanceStore implements AttendanceStore {
    final Map<String, FacultyRecord> faculty = new HashMap<String, FacultyRecord>();
    final List<AttendanceRecord> records = new ArrayList<AttendanceRecord>();
    final List<AuditEntry> audits = new ArrayList<AuditEntry>();
    private final ReentrantLock lock = new ReentrantLock();
    private long nextId = 1;
    int insertCalls = 0;
    RuntimeException failOnInsert;       // inject a database failure

    @Override public <T> T inTransaction(Callable<T> work) {
        lock.lock();
        try {
            return work.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    void addFaculty(String uid, String name, boolean active) {
        faculty.put(uid, new FacultyRecord(uid, name, "CS", active, "model-x", false, null));
    }

    @Override public FacultyRecord findFaculty(String uid) { return faculty.get(uid); }

    @Override public AttendanceRecord findOpenSession(String uid) {
        for (AttendanceRecord r : records) if (r.facultyUid().equals(uid) && r.isOpen()) return r;
        return null;
    }

    @Override public AttendanceRecord findLatestClosedSession(String uid) {
        AttendanceRecord best = null;
        for (AttendanceRecord r : records) {
            if (r.facultyUid().equals(uid) && r.logoutAt() != null
                    && (best == null || r.logoutAt().isAfter(best.logoutAt()))) best = r;
        }
        return best;
    }

    @Override public AttendanceRecord findById(long id) {
        for (AttendanceRecord r : records) if (r.id() == id) return r;
        return null;
    }

    @Override public AttendanceRecord insert(AttendanceRecord record) {
        insertCalls++;
        if (failOnInsert != null) throw failOnInsert;
        if (record.isOpen() && findOpenSession(record.facultyUid()) != null)
            throw new DuplicateOpenSessionException("unique", null);
        AttendanceRecord saved = record.withId(nextId++);
        records.add(saved);
        return saved;
    }

    @Override public void update(AttendanceRecord record) {
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).id() == record.id()) { records.set(i, record); return; }
        }
        throw new IllegalStateException("no such record");
    }

    @Override public void insertAudit(AuditEntry entry) { audits.add(entry); }
}
