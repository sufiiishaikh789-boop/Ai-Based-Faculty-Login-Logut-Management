package com.example.facultyattendance.data.repository;

import com.example.facultyattendance.data.local.AttendanceEntity;
import com.example.facultyattendance.data.local.AuditEntity;
import com.example.facultyattendance.data.local.FacultyEntity;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.AttendanceStatus;
import com.example.facultyattendance.domain.AuditEntry;
import com.example.facultyattendance.domain.FacultyRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Entity <-> domain mapping. Keeps Room types out of the business logic. */
final class Mappers {
    private Mappers() { }

    static FacultyRecord toDomain(FacultyEntity e) {
        if (e == null) return null;
        return new FacultyRecord(e.uid, e.name, e.department, e.active,
                e.encryptedTemplate == null ? null : e.templateModelId, e.templateDemo,
                e.enrolledAtMillis == 0 ? null : Instant.ofEpochMilli(e.enrolledAtMillis));
    }

    static AttendanceRecord toDomain(AttendanceEntity e) {
        if (e == null) return null;
        return new AttendanceRecord(e.id, e.facultyUid, e.facultyName, e.localDate,
                Instant.ofEpochMilli(e.loginAtMillis),
                e.logoutAtMillis == null ? null : Instant.ofEpochMilli(e.logoutAtMillis),
                AttendanceStatus.valueOf(e.status), e.loginEvidence, e.logoutEvidence, e.demo, e.corrected);
    }

    static AttendanceEntity toEntity(AttendanceRecord r) {
        AttendanceEntity e = new AttendanceEntity();
        e.id = r.id();
        e.facultyUid = r.facultyUid();
        e.facultyName = r.facultyName();
        e.localDate = r.localDate();
        e.loginAtMillis = r.loginAt().toEpochMilli();
        e.logoutAtMillis = r.logoutAt() == null ? null : r.logoutAt().toEpochMilli();
        e.status = r.status().name();
        e.openMarker = r.isOpen() ? Integer.valueOf(1) : null;   // drives the unique "one open session" index
        e.loginEvidence = r.loginEvidence();
        e.logoutEvidence = r.logoutEvidence();
        e.demo = r.isDemo();
        e.corrected = r.isCorrected();
        return e;
    }

    static AuditEntity toEntity(AuditEntry a) {
        AuditEntity e = new AuditEntity();
        e.id = a.id();
        e.action = a.action();
        e.recordId = a.recordId();
        e.targetUid = a.targetUid();
        e.atMillis = a.at().toEpochMilli();
        e.actor = a.actor();
        e.reason = a.reason();
        e.details = a.details();
        return e;
    }

    static AuditEntry toDomain(AuditEntity e) {
        return new AuditEntry(e.id, e.action, e.recordId, e.targetUid,
                Instant.ofEpochMilli(e.atMillis), e.actor, e.reason, e.details);
    }

    static List<AttendanceRecord> attendanceList(List<AttendanceEntity> in) {
        List<AttendanceRecord> out = new ArrayList<AttendanceRecord>(in.size());
        for (AttendanceEntity e : in) out.add(toDomain(e));
        return out;
    }
}
