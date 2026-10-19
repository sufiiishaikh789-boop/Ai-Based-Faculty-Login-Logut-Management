package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.facultyattendance.attendance.AttendanceService;
import com.example.facultyattendance.config.AttendancePolicy;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.EligibilityResult;
import com.example.facultyattendance.domain.IdentityResult;
import com.example.facultyattendance.domain.ScanResult;
import com.example.facultyattendance.domain.ScanResult.Type;

import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AttendanceServiceTest {
    private static final Instant T0 = Instant.parse("2026-03-02T04:00:00Z");

    private FakeAttendanceStore store;
    private MutableClock clock;
    private AttendanceService service;
    private final AtomicInteger reported = new AtomicInteger();

    @Before
    public void setUp() {
        store = new FakeAttendanceStore();
        store.addFaculty("F-100", "Test Person", true);
        store.addFaculty("F-OFF", "Inactive Person", false);
        clock = new MutableClock(T0);
        service = newService(AttendancePolicy.defaults(ZoneOffset.UTC), false);
    }

    private AttendanceService newService(AttendancePolicy p, boolean allowDemo) {
        return new AttendanceService(store, clock, p, allowDemo, new Consumer<Throwable>() {
            @Override public void accept(Throwable t) { reported.incrementAndGet(); }
        });
    }

    private IdentityResult who(String uid) { return IdentityResult.verified(uid, 0.91f, "test-model", false); }
    private EligibilityResult ok() { return EligibilityResult.eligibleWifi("WIFI:ssid_match", clock.instant()); }

    @Test public void firstValidScanCreatesLogin() {
        ScanResult r = service.recordScan(who("F-100"), ok());
        assertEquals(Type.LOGIN_RECORDED, r.type());
        assertEquals(1, store.records.size());
        AttendanceRecord rec = store.records.get(0);
        assertEquals(T0, rec.loginAt());
        assertNull(rec.logoutAt());
        assertEquals("2026-03-02", rec.localDate());
        assertEquals("Test Person", rec.facultyName());
        assertEquals("WIFI:ssid_match", rec.loginEvidence());
        assertTrue(rec.isOpen());
    }

    @Test public void repeatedScanBeforeIntervalIsIgnoredAndKeepsOriginalTimestamp() {
        service.recordScan(who("F-100"), ok());
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        ScanResult r = service.recordScan(who("F-100"), ok());
        assertEquals(Type.DUPLICATE_IGNORED, r.type());
        assertEquals(1, store.records.size());
        assertEquals(T0, store.records.get(0).loginAt());
        assertNull(store.records.get(0).logoutAt());
        assertEquals(Duration.ofSeconds(1), r.retryAfter());
    }

    @Test public void scanAtOrAfterIntervalRecordsLogout() {
        service.recordScan(who("F-100"), ok());
        clock.advance(Duration.ofMinutes(15));
        ScanResult r = service.recordScan(who("F-100"), ok());
        assertEquals(Type.LOGOUT_RECORDED, r.type());
        assertEquals(1, store.records.size());
        AttendanceRecord rec = store.records.get(0);
        assertEquals(T0.plus(Duration.ofMinutes(15)), rec.logoutAt());
        assertEquals(T0, rec.loginAt());
        assertFalse(rec.isOpen());
        assertEquals("COMPLETED", rec.sessionState());
    }

    @Test public void laterScanAfterLogoutOpensNewSession() {
        service.recordScan(who("F-100"), ok());
        clock.advance(Duration.ofMinutes(20));
        service.recordScan(who("F-100"), ok());                  // logout
        clock.advance(Duration.ofMinutes(5));
        ScanResult r = service.recordScan(who("F-100"), ok());   // new login
        assertEquals(Type.LOGIN_RECORDED, r.type());
        assertEquals(2, store.records.size());
        assertEquals(1, countOpen());
    }

    @Test public void scanImmediatelyAfterLogoutIsIgnoredByCooldown() {
        service.recordScan(who("F-100"), ok());
        clock.advance(Duration.ofMinutes(20));
        service.recordScan(who("F-100"), ok());                  // logout
        clock.advance(Duration.ofSeconds(10));
        ScanResult r = service.recordScan(who("F-100"), ok());
        assertEquals(Type.DUPLICATE_IGNORED, r.type());
        assertEquals(1, store.records.size());
    }

    @Test public void cooldownCanBeDisabledForTheLiteralRule() {
        AttendancePolicy p = new AttendancePolicy(Duration.ofMinutes(15), Duration.ZERO,
                Duration.ofMinutes(2), ZoneOffset.UTC);
        service = newService(p, false);
        service.recordScan(who("F-100"), ok());
        clock.advance(Duration.ofMinutes(20));
        service.recordScan(who("F-100"), ok());
        ScanResult r = service.recordScan(who("F-100"), ok());
        assertEquals(Type.LOGIN_RECORDED, r.type());
    }

    @Test public void intervalIsConfigurable() {
        AttendancePolicy p = new AttendancePolicy(Duration.ofMinutes(1), Duration.ZERO,
                Duration.ofMinutes(2), ZoneOffset.UTC);
        service = newService(p, false);
        service.recordScan(who("F-100"), ok());
        clock.advance(Duration.ofSeconds(61));
        assertEquals(Type.LOGOUT_RECORDED, service.recordScan(who("F-100"), ok()).type());
    }

    @Test public void missingLogoutStaysAnOpenSessionAndIsNeverFabricated() {
        service.recordScan(who("F-100"), ok());
        clock.advance(Duration.ofHours(10));                     // nobody scans again
        AttendanceRecord rec = store.records.get(0);
        assertNull(rec.logoutAt());
        assertTrue(rec.isOpen());
        assertEquals("OPEN_SESSION", rec.sessionState());
    }

    @Test public void unknownFacultyIsRejected() {
        ScanResult r = service.recordScan(who("NOPE-1"), ok());
        assertEquals(Type.REJECTED_UNKNOWN_FACULTY, r.type());
        assertEquals(0, store.records.size());
    }

    @Test public void inactiveFacultyIsRejected() {
        ScanResult r = service.recordScan(who("F-OFF"), ok());
        assertEquals(Type.REJECTED_INACTIVE_FACULTY, r.type());
        assertEquals(0, store.records.size());
    }

    @Test public void faceMismatchNeverWritesAttendance() {
        IdentityResult.Outcome[] bad = {
                IdentityResult.Outcome.MISMATCH, IdentityResult.Outcome.NO_FACE,
                IdentityResult.Outcome.MULTIPLE_FACES, IdentityResult.Outcome.AMBIGUOUS,
                IdentityResult.Outcome.LIVENESS_FAILED, IdentityResult.Outcome.MODEL_NOT_READY,
                IdentityResult.Outcome.LOW_QUALITY, IdentityResult.Outcome.ERROR };
        for (IdentityResult.Outcome o : bad) {
            ScanResult r = service.recordScan(IdentityResult.failed(o, "m", false), ok());
            assertEquals(Type.REJECTED_IDENTITY, r.type());
        }
        assertEquals(Type.REJECTED_IDENTITY, service.recordScan(null, ok()).type());
        assertEquals(0, store.insertCalls);
        assertEquals(0, store.records.size());
    }

    @Test public void campusFailureNeverWritesAttendance() {
        EligibilityResult[] bad = {
                EligibilityResult.rejected(EligibilityResult.Outcome.NOT_CONFIGURED, "nc", clock.instant()),
                EligibilityResult.rejected(EligibilityResult.Outcome.OUT_OF_RANGE, "far", clock.instant()),
                EligibilityResult.rejected(EligibilityResult.Outcome.MOCK_LOCATION, "mock", clock.instant()) };
        for (EligibilityResult e : bad) {
            assertEquals(Type.REJECTED_CAMPUS, service.recordScan(who("F-100"), e).type());
        }
        assertEquals(Type.REJECTED_CAMPUS, service.recordScan(who("F-100"), null).type());
        assertEquals(0, store.insertCalls);
    }

    @Test public void staleCampusCheckIsRejected() {
        EligibilityResult old = EligibilityResult.eligibleWifi("WIFI:ssid_match", clock.instant());
        clock.advance(Duration.ofMinutes(3));
        assertEquals(Type.REJECTED_CAMPUS, service.recordScan(who("F-100"), old).type());
        assertEquals(0, store.records.size());
    }

    @Test public void demoRecordsAreRefusedUnlessExplicitlyAllowed() {
        IdentityResult demoId = IdentityResult.verified("F-100", 0.9f, "demo", true);
        assertEquals(Type.REJECTED_DEMO_NOT_ALLOWED, service.recordScan(demoId, ok()).type());
        EligibilityResult bypass = EligibilityResult.demoBypass("demo", clock.instant());
        assertEquals(Type.REJECTED_DEMO_NOT_ALLOWED, service.recordScan(who("F-100"), bypass).type());
        assertEquals(0, store.records.size());

        service = newService(AttendancePolicy.defaults(ZoneOffset.UTC), true);
        assertEquals(Type.LOGIN_RECORDED, service.recordScan(demoId, bypass).type());
        assertTrue(store.records.get(0).isDemo());
    }

    @Test public void clockGoingBackwardsIsTreatedAsDuplicateNotLogout() {
        service.recordScan(who("F-100"), ok());
        clock.set(T0.minusSeconds(30));
        EligibilityResult fresh = EligibilityResult.eligibleWifi("WIFI:ssid_match", clock.instant());
        assertEquals(Type.DUPLICATE_IGNORED, service.recordScan(who("F-100"), fresh).type());
        assertNull(store.records.get(0).logoutAt());
    }

    @Test public void databaseFailureIsReportedAndLeavesNoRecord() {
        store.failOnInsert = new IllegalStateException("disk full");
        ScanResult r = service.recordScan(who("F-100"), ok());
        assertEquals(Type.ERROR, r.type());
        assertEquals(1, reported.get());
        assertEquals(0, store.records.size());
    }

    @Test public void concurrentScansProduceExactlyOneLogin() throws Exception {
        final int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch go = new CountDownLatch(1);
        List<Future<ScanResult>> futures = new ArrayList<Future<ScanResult>>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(new java.util.concurrent.Callable<ScanResult>() {
                @Override public ScanResult call() throws Exception {
                    go.await();
                    return service.recordScan(who("F-100"), ok());
                }
            }));
        }
        go.countDown();
        int logins = 0, dups = 0;
        for (Future<ScanResult> f : futures) {
            Type t = f.get(10, TimeUnit.SECONDS).type();
            if (t == Type.LOGIN_RECORDED) logins++;
            if (t == Type.DUPLICATE_IGNORED) dups++;
        }
        pool.shutdownNow();
        assertEquals(1, logins);
        assertEquals(threads - 1, dups);
        assertEquals(1, store.records.size());
        assertEquals(1, countOpen());
    }

    private int countOpen() {
        int n = 0;
        for (AttendanceRecord r : store.records) if (r.isOpen()) n++;
        return n;
    }
}
