package com.example.facultyattendance.data.repository;

import com.example.facultyattendance.attendance.AttendanceCsvExporter;
import com.example.facultyattendance.data.local.AppDatabase;
import com.example.facultyattendance.authentication.AdminAuthManager;
import com.example.facultyattendance.data.local.AuditEntity;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.AuditEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Read side of attendance (queries, audit list, CSV export). All methods block: call them from a
 * background executor. Writes go through AttendanceService / AdminCorrectionService.
 * Keep this class free of Android UI types so an optional remote backend can be added behind it later.
 */
public final class AttendanceRepository {
    private final AppDatabase db;

    public AttendanceRepository(AppDatabase db) { this.db = db; }

    /** @param state null (all), "OPEN_SESSION" or "COMPLETED"; dates are yyyy-MM-dd or null. */
    public List<AttendanceRecord> search(String text, String fromDate, String toDate, String state, int limit) {
        String q = text == null || text.trim().isEmpty() ? null : escapeLike(text.trim());
        return Mappers.attendanceList(db.attendanceDao().search(q, fromDate, toDate, state, limit));
    }

    public List<AttendanceRecord> recentFor(String uid, int limit) {
        return Mappers.attendanceList(db.attendanceDao().recent(uid, limit));
    }

    public List<AttendanceRecord> forDate(String uid, String localDate) {
        return Mappers.attendanceList(db.attendanceDao().forDate(uid, localDate));
    }

    public List<AttendanceRecord> openSessions() {
        return Mappers.attendanceList(db.attendanceDao().openSessions());
    }

    public List<AuditEntry> recentAudit(int limit) {
        List<AuditEntry> out = new ArrayList<AuditEntry>();
        for (AuditEntity e : db.auditDao().recent(limit)) out.add(Mappers.toDomain(e));
        return out;
    }

    /** Writes a generic administrator audit line (exports, configuration changes). Never put secrets or coordinates in details. */
    public void recordAudit(String action, String details, long nowMillis) {
        AuditEntity a = new AuditEntity();
        a.action = action;
        a.atMillis = nowMillis;
        a.actor = AdminAuthManager.ACTOR;
        a.details = details;
        db.auditDao().insert(a);
    }

    public int totalRecords() { return db.attendanceDao().count(); }

    /** Writes a UTF-8 CSV (with BOM so Excel on Windows detects the encoding). */
    public void writeCsv(OutputStream out, List<AttendanceRecord> records, ZoneId zone) throws IOException {
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        out.write(AttendanceCsvExporter.toCsv(records, zone).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
