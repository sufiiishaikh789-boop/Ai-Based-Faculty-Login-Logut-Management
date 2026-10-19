package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.facultyattendance.attendance.AttendanceService;
import com.example.facultyattendance.config.AttendancePolicy;
import com.example.facultyattendance.data.local.AppDatabase;
import com.example.facultyattendance.data.local.AttendanceEntity;
import com.example.facultyattendance.data.local.FacultyEntity;
import com.example.facultyattendance.data.repository.AttendanceRepository;
import com.example.facultyattendance.data.repository.RoomAttendanceStore;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.EligibilityResult;
import com.example.facultyattendance.domain.IdentityResult;
import com.example.facultyattendance.domain.ScanResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs the attendance state machine against a REAL (in-memory) Room database on a device or
 * emulator. All people and values below are synthetic.
 */
@RunWith(AndroidJUnit4.class)
public class RoomAttendanceIntegrationTest {
    private AppDatabase db;
    private RoomAttendanceStore store;
    private final Instant[] now = { Instant.parse("2026-03-02T04:00:00Z") };
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now[0]; }
    };

    @Before public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).allowMainThreadQueries().build();
        store = new RoomAttendanceStore(db);
        insertFaculty("F-100", "Test Person", true);
    }

    @After public void tearDown() { db.close(); }

    private void insertFaculty(String uid, String name, boolean active) {
        FacultyEntity f = new FacultyEntity();
        f.uid = uid; f.name = name; f.department = "CS"; f.active = active;
        f.createdAtMillis = f.updatedAtMillis = 1L;
        db.facultyDao().insert(f);
    }

    private AttendanceService service() {
        return new AttendanceService(store, clock, AttendancePolicy.defaults(ZoneOffset.UTC), false, null);
    }

    private IdentityResult who() { return IdentityResult.verified("F-100", 0.9f, "synthetic-model", false); }
    private EligibilityResult ok() { return EligibilityResult.eligibleWifi("WIFI:ssid_match", clock.instant()); }

    @Test public void duplicateUidIsRejectedByTheDatabase() {
        try {
            insertFaculty("F-100", "Someone Else", true);
            fail("duplicate UID must not be accepted");
        } catch (SQLiteConstraintException expected) { }
        assertEquals("Test Person", db.facultyDao().get("F-100").name);
    }

    @Test public void fullLifecycleLoginDuplicateLogoutNewSession() {
        assertEquals(ScanResult.Type.LOGIN_RECORDED, service().recordScan(who(), ok()).type());
        now[0] = now[0].plusSeconds(60 * 5);
        assertEquals(ScanResult.Type.DUPLICATE_IGNORED, service().recordScan(who(), ok()).type());
        now[0] = now[0].plus(Duration.ofMinutes(11));
        assertEquals(ScanResult.Type.LOGOUT_RECORDED, service().recordScan(who(), ok()).type());
        now[0] = now[0].plus(Duration.ofMinutes(5));
        assertEquals(ScanResult.Type.LOGIN_RECORDED, service().recordScan(who(), ok()).type());
        assertEquals(2, db.attendanceDao().count());
        assertNotNull(db.attendanceDao().findOpen("F-100"));
    }

    @Test public void databaseItselfRefusesASecondOpenSession() {
        AttendanceRecord a = AttendanceRecord.newLogin("F-100", "Test Person", "2026-03-02", now[0], "x", false);
        store.insert(a);
        try {
            db.attendanceDao().insert(rawOpenEntity());
            fail("unique (facultyUid, openMarker) index must reject a second open session");
        } catch (SQLiteConstraintException expected) { }
        assertEquals(1, db.attendanceDao().count());
    }

    private AttendanceEntity rawOpenEntity() {
        AttendanceEntity e = new AttendanceEntity();
        e.facultyUid = "F-100"; e.facultyName = "Test Person"; e.localDate = "2026-03-02";
        e.loginAtMillis = now[0].toEpochMilli(); e.openMarker = 1;
        return e;
    }

    @Test public void concurrentScansCreateExactlyOneOpenSession() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        final CountDownLatch go = new CountDownLatch(1);
        List<Future<ScanResult>> results = new ArrayList<>();
        final AttendanceService svc = service();
        for (int i = 0; i < 16; i++) {
            results.add(pool.submit(() -> { go.await(); return svc.recordScan(who(), ok()); }));
        }
        go.countDown();
        int logins = 0;
        for (Future<ScanResult> f : results) {
            if (f.get(20, TimeUnit.SECONDS).type() == ScanResult.Type.LOGIN_RECORDED) logins++;
        }
        pool.shutdownNow();
        assertEquals(1, logins);
        assertEquals(1, db.attendanceDao().count());
    }

    @Test public void searchFiltersByTextDateAndState() {
        service().recordScan(who(), ok());                              // open session on 2026-03-02
        AttendanceRepository repo = new AttendanceRepository(db);
        assertEquals(1, repo.search("test", null, null, null, 50).size());
        assertEquals(1, repo.search("F-1", "2026-03-01", "2026-03-03", "OPEN_SESSION", 50).size());
        assertEquals(0, repo.search(null, null, null, "COMPLETED", 50).size());
        assertEquals(0, repo.search(null, "2026-03-03", null, null, 50).size());
        assertEquals(0, repo.search("100%", null, null, null, 50).size());   // wildcard characters are escaped
        assertNull(db.attendanceDao().findLatestClosed("F-100"));
        assertTrue(repo.openSessions().get(0).isOpen());
    }
}
