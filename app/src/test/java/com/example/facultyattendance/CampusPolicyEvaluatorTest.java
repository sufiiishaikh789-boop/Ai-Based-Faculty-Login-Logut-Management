package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.facultyattendance.config.CampusConfig;
import com.example.facultyattendance.domain.EligibilityResult;
import com.example.facultyattendance.domain.EligibilityResult.Outcome;
import com.example.facultyattendance.location.CampusPolicyEvaluator;
import com.example.facultyattendance.location.LocationFetchResult;
import com.example.facultyattendance.location.LocationFetchResult.Status;
import com.example.facultyattendance.location.LocationSnapshot;
import com.example.facultyattendance.location.LocationSource;
import com.example.facultyattendance.location.WifiSnapshot;
import com.example.facultyattendance.location.WifiVerdict;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** All coordinates and network names below are synthetic test values, not a real campus. */
public class CampusPolicyEvaluatorTest {
    private static final double LAT = 12.0000, LON = 77.0000;
    private MutableClock clock;
    private CampusConfig full;
    private final AtomicInteger gpsCalls = new AtomicInteger();

    @Before public void setUp() {
        clock = new MutableClock(Instant.parse("2026-03-02T04:00:00Z"));
        full = CampusConfig.builder().addSsid("TEST-CAMPUS").center(LAT, LON).build();
    }

    private LocationSource gps(final LocationFetchResult r) {
        return new LocationSource() {
            @Override public LocationFetchResult fetch() { gpsCalls.incrementAndGet(); return r; }
        };
    }

    private LocationFetchResult fix(double dLonDeg, double acc, long ageMs, boolean mock) {
        return LocationFetchResult.ok(new LocationSnapshot(LAT, LON + dLonDeg, acc, ageMs, mock));
    }

    private static final WifiSnapshot OFF_CAMPUS = new WifiSnapshot(true, true, "\"CoffeeShop\"", "aa:bb:cc:dd:ee:01");
    private static final WifiSnapshot ON_CAMPUS = new WifiSnapshot(true, true, "\"TEST-CAMPUS\"", "aa:bb:cc:dd:ee:02");

    private EligibilityResult eval(CampusConfig cfg, WifiSnapshot w, LocationFetchResult r, boolean demo) {
        return CampusPolicyEvaluator.evaluate(cfg, w, gps(r), clock, demo);
    }

    @Test public void placeholderConfigFailsSafelyAndNeverTouchesGps() {
        CampusConfig empty = CampusConfig.unconfigured();
        assertFalse(empty.isUsable());
        assertFalse(empty.validate().isEmpty());
        EligibilityResult r = eval(empty, ON_CAMPUS, fix(0, 5, 100, false), false);
        assertEquals(Outcome.NOT_CONFIGURED, r.outcome());
        assertFalse(r.isEligible());
        assertEquals(0, gpsCalls.get());
    }

    @Test public void invalidCoordinatesAndRadiusMakeConfigInvalid() {
        assertFalse(CampusConfig.builder().center(0.0, 0.0).build().isUsable());
        assertFalse(CampusConfig.builder().center(95.0, 10.0).build().isUsable());
        assertFalse(CampusConfig.builder().center(12.0, 77.0).radiusMeters(0).build().isUsable());
        assertFalse(CampusConfig.builder().addSsid("X").addBssid("not-a-bssid").build().isUsable());
        assertTrue(full.isUsable());
        assertEquals(10.0, full.radiusMeters(), 0.0);   // default radius is 10 m
    }

    @Test public void demoModeBypassIsExplicitlyFlagged() {
        EligibilityResult r = eval(CampusConfig.unconfigured(), null, LocationFetchResult.failure(Status.ERROR), true);
        assertEquals(Outcome.DEMO_BYPASS, r.outcome());
        assertTrue(r.isEligible());
        assertTrue(r.isDemo());
    }

    @Test public void trustedWifiIsEnoughAndGpsIsNotConsulted() {
        EligibilityResult r = eval(full, ON_CAMPUS, LocationFetchResult.failure(Status.TIMEOUT), false);
        assertEquals(Outcome.ELIGIBLE_WIFI, r.outcome());
        assertEquals(0, gpsCalls.get());
    }

    @Test public void arbitraryWifiIsNeverTreatedAsCampus() {
        CampusConfig wifiOnly = CampusConfig.builder().addSsid("TEST-CAMPUS").build();
        EligibilityResult r = eval(wifiOnly, OFF_CAMPUS, null, false);
        assertEquals(Outcome.WIFI_NOT_TRUSTED, r.outcome());
        assertFalse(r.isEligible());
    }

    @Test public void wifiStatesAreClassified() {
        assertEquals(WifiVerdict.WIFI_OFF, CampusPolicyEvaluator.evaluateWifi(full, new WifiSnapshot(false, false, null, null)));
        assertEquals(WifiVerdict.NOT_CONNECTED, CampusPolicyEvaluator.evaluateWifi(full, new WifiSnapshot(true, false, null, null)));
        assertEquals(WifiVerdict.SSID_UNREADABLE, CampusPolicyEvaluator.evaluateWifi(full, new WifiSnapshot(true, true, "<unknown ssid>", null)));
        assertEquals(WifiVerdict.SSID_MISMATCH, CampusPolicyEvaluator.evaluateWifi(full, OFF_CAMPUS));
        assertEquals(WifiVerdict.TRUSTED, CampusPolicyEvaluator.evaluateWifi(full, ON_CAMPUS));
    }

    @Test public void bssidAllowlistIsEnforcedWhenConfigured() {
        CampusConfig cfg = CampusConfig.builder().addSsid("TEST-CAMPUS").addBssid("AA:BB:CC:DD:EE:02").build();
        assertEquals(WifiVerdict.TRUSTED, CampusPolicyEvaluator.evaluateWifi(cfg, ON_CAMPUS));
        WifiSnapshot rogue = new WifiSnapshot(true, true, "\"TEST-CAMPUS\"", "aa:bb:cc:dd:ee:99");
        assertEquals(WifiVerdict.BSSID_MISMATCH, CampusPolicyEvaluator.evaluateWifi(cfg, rogue));
        WifiSnapshot hidden = new WifiSnapshot(true, true, "\"TEST-CAMPUS\"", "02:00:00:00:00:00");
        assertEquals(WifiVerdict.SSID_UNREADABLE, CampusPolicyEvaluator.evaluateWifi(cfg, hidden));
    }

    @Test public void gpsInsideTenMetresWithGoodAccuracyIsEligible() {
        // 0.00004 deg of longitude near lat 12 is ~4.4 m
        EligibilityResult r = eval(full, OFF_CAMPUS, fix(0.00004, 5.0, 1000, false), false);
        assertEquals(Outcome.ELIGIBLE_GPS, r.outcome());
        assertTrue(r.evidence().startsWith("GPS:dist="));
        assertEquals(1, gpsCalls.get());
    }

    @Test public void gpsOutsideRadiusIsRejected() {
        // 0.0002 deg of longitude near lat 12 is ~21.8 m
        assertEquals(Outcome.OUT_OF_RANGE, eval(full, OFF_CAMPUS, fix(0.0002, 5.0, 1000, false), false).outcome());
    }

    @Test public void inaccurateStaleAndMockFixesAreRejected() {
        assertEquals(Outcome.LOW_ACCURACY, eval(full, OFF_CAMPUS, fix(0, 25.0, 1000, false), false).outcome());
        assertEquals(Outcome.LOW_ACCURACY, eval(full, OFF_CAMPUS, fix(0, 0.0, 1000, false), false).outcome());
        assertEquals(Outcome.STALE_LOCATION, eval(full, OFF_CAMPUS, fix(0, 5.0, 60_000, false), false).outcome());
        assertEquals(Outcome.MOCK_LOCATION, eval(full, OFF_CAMPUS, fix(0, 5.0, 1000, true), false).outcome());
    }

    @Test public void locationProblemsMapToHelpfulOutcomes() {
        assertEquals(Outcome.PERMISSION_DENIED, eval(full, OFF_CAMPUS, LocationFetchResult.failure(Status.PERMISSION_DENIED), false).outcome());
        assertEquals(Outcome.APPROXIMATE_ONLY, eval(full, OFF_CAMPUS, LocationFetchResult.failure(Status.APPROXIMATE_ONLY), false).outcome());
        assertEquals(Outcome.LOCATION_DISABLED, eval(full, OFF_CAMPUS, LocationFetchResult.failure(Status.LOCATION_DISABLED), false).outcome());
        assertEquals(Outcome.LOCATION_TIMEOUT, eval(full, OFF_CAMPUS, LocationFetchResult.failure(Status.TIMEOUT), false).outcome());
        assertEquals(Outcome.LOCATION_ERROR, eval(full, OFF_CAMPUS, LocationFetchResult.failure(Status.ERROR), false).outcome());
    }
}
