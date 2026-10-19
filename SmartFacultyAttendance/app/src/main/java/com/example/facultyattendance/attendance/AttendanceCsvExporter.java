package com.example.facultyattendance.attendance;

import com.example.facultyattendance.domain.AttendanceRecord;

import java.time.ZoneId;
import java.util.List;

public final class AttendanceCsvExporter {
    private AttendanceCsvExporter() { }

    public static String toCsv(List<AttendanceRecord> records, ZoneId zone) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvWriter.row("record_id", "faculty_uid", "faculty_name", "date", "login_local",
                "logout_local", "session_state", "demo", "admin_corrected", "login_evidence", "logout_evidence"));
        for (AttendanceRecord r : records) {
            sb.append(CsvWriter.row(
                    String.valueOf(r.id()),
                    r.facultyUid(),
                    r.facultyName(),
                    r.localDate(),
                    r.loginAt().atZone(zone).toOffsetDateTime().toString(),
                    r.logoutAt() == null ? "" : r.logoutAt().atZone(zone).toOffsetDateTime().toString(),
                    r.sessionState(),
                    r.isDemo() ? "DEMO" : "REAL",
                    r.isCorrected() ? "YES" : "NO",
                    r.loginEvidence(),
                    r.logoutEvidence()));
        }
        return sb.toString();
    }
}
