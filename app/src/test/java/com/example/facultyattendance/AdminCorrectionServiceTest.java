package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.facultyattendance.attendance.AdminCorrectionService;
import com.example.facultyattendance.domain.AttendanceRecord;

import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

public class AdminCorrectionServiceTest {
    private static final Instant LOGIN = Instant.parse("2026-03-02T04:00:00Z");
    private FakeAttendanceStore store;
    private MutableClock clock;
    private AdminCorrectionService svc;
    private long openId;

    @Before public void setUp() {
        store = new FakeAttendanceStore();
        store.addFaculty("F-100", "Test Person", true);
        clock = new MutableClock(LOGIN.plus(Duration.ofHours(9)));
        svc = new AdminCorrectionService(store, clock);
        openId = store.insert(AttendanceRecord.newLogin("F-100", "Test Person", "2026-03-02", LOGIN, "WIFI:ssid_match", false)).id();
    }

    @Test public void closesOpenSessionAndWritesAuditEntry() {
        Instant out = LOGIN.plus(Duration.ofHours(8));
        AdminCorrectionService.Result r = svc.closeOpenSession(openId, out, "Forgot to scan out", "admin");
        assertTrue(r.message, r.ok);
        AttendanceRecord rec = store.findById(openId);
        assertEquals(out, rec.logoutAt());
        assertTrue(rec.isCorrected());
        assertEquals("ADMIN_CORRECTION", rec.logoutEvidence());
        assertEquals(1, store.audits.size());
        assertEquals("Forgot to scan out", store.audits.get(0).reason());
        assertEquals("admin", store.audits.get(0).actor());
        assertEquals(Long.valueOf(openId), store.audits.get(0).recordId());
    }

    @Test public void rejectsMissingReasonBeforeLoginFutureAndCompletedSessions() {
        Instant ok = LOGIN.plus(Duration.ofHours(1));
        assertFalse(svc.closeOpenSession(openId, ok, "  ", "admin").ok);
        assertFalse(svc.closeOpenSession(openId, ok, "abc", "admin").ok);
        assertFalse(svc.closeOpenSession(openId, LOGIN.minusSeconds(1), "valid reason", "admin").ok);
        assertFalse(svc.closeOpenSession(openId, clock.instant().plusSeconds(60), "valid reason", "admin").ok);
        assertFalse(svc.closeOpenSession(9999, ok, "valid reason", "admin").ok);
        assertFalse(svc.closeOpenSession(openId, ok, "valid reason", " ").ok);
        assertNull(store.findById(openId).logoutAt());
        assertEquals(0, store.audits.size());

        assertTrue(svc.closeOpenSession(openId, ok, "valid reason", "admin").ok);
        assertFalse(svc.closeOpenSession(openId, ok, "valid reason", "admin").ok);   // already closed
        assertEquals(1, store.audits.size());
    }
}
