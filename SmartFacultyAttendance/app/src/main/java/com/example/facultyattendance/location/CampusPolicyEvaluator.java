package com.example.facultyattendance.location;

import com.example.facultyattendance.config.CampusConfig;
import com.example.facultyattendance.domain.EligibilityResult;
import com.example.facultyattendance.domain.EligibilityResult.Outcome;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Pure decision logic for the campus restriction. Android code only supplies a WifiSnapshot and a
 * LocationSource; every policy decision is made here so it can be unit tested.
 *
 * Fail-safe rule: missing or invalid configuration is NEVER treated as "eligible" (except the
 * explicit, visibly flagged demo bypass).
 */
public final class CampusPolicyEvaluator {
    private CampusPolicyEvaluator() { }

    public static EligibilityResult evaluate(CampusConfig cfg, WifiSnapshot wifi, LocationSource gps,
                                             Clock clock, boolean demoMode) {
        Instant now = clock.instant();
        if (demoMode) {
            return EligibilityResult.demoBypass("DEMO MODE: campus check skipped. Records are flagged DEMO.", now);
        }
        List<String> problems = cfg.validate();
        if (!problems.isEmpty() || !(cfg.isWifiConfigured() || cfg.isGpsConfigured())) {
            String why = problems.isEmpty() ? "nothing configured" : problems.get(0);
            return EligibilityResult.rejected(Outcome.NOT_CONFIGURED,
                    "Campus restriction is not configured (" + why + "). Ask an administrator.", now);
        }

        WifiVerdict verdict = evaluateWifi(cfg, wifi);
        if (verdict.isTrusted()) {
            String ev = cfg.trustedBssids().isEmpty() ? "WIFI:ssid_match" : "WIFI:ssid_bssid_match";
            return EligibilityResult.eligibleWifi(ev, now);
        }
        if (!cfg.isGpsConfigured()) {
            return EligibilityResult.rejected(Outcome.WIFI_NOT_TRUSTED, verdict.userMessage(), now);
        }

        LocationFetchResult fetched = gps.fetch();
        Instant afterFetch = clock.instant();
        switch (fetched.status()) {
            case OK:
                return evaluateLocation(cfg, fetched.snapshot(), afterFetch);
            case PERMISSION_DENIED:
                return EligibilityResult.rejected(Outcome.PERMISSION_DENIED,
                        "Location permission is required when not on campus Wi-Fi.", afterFetch);
            case APPROXIMATE_ONLY:
                return EligibilityResult.rejected(Outcome.APPROXIMATE_ONLY,
                        "Precise location is required. Allow 'Precise' location for this app.", afterFetch);
            case LOCATION_DISABLED:
                return EligibilityResult.rejected(Outcome.LOCATION_DISABLED,
                        "Turn on Location (GPS) and try again.", afterFetch);
            case TIMEOUT:
                return EligibilityResult.rejected(Outcome.LOCATION_TIMEOUT,
                        "No GPS fix in time. Move near a window or outdoors and retry.", afterFetch);
            default:
                return EligibilityResult.rejected(Outcome.LOCATION_ERROR,
                        "Location could not be determined. Retry.", afterFetch);
        }
    }

    public static WifiVerdict evaluateWifi(CampusConfig cfg, WifiSnapshot s) {
        if (!cfg.isWifiConfigured()) return WifiVerdict.NOT_CONFIGURED;
        if (s == null || !s.wifiEnabled()) return WifiVerdict.WIFI_OFF;
        if (!s.connected()) return WifiVerdict.NOT_CONNECTED;
        String ssid = CampusConfig.normalizeSsid(s.ssid());
        if (ssid == null || ssid.isEmpty() || "<unknown ssid>".equals(ssid)) return WifiVerdict.SSID_UNREADABLE;
        if (!cfg.trustedSsids().contains(ssid)) return WifiVerdict.SSID_MISMATCH;
        if (!cfg.trustedBssids().isEmpty()) {
            String b = CampusConfig.normalizeBssid(s.bssid());
            if (b == null || b.isEmpty() || b.equals(CampusConfig.REDACTED_BSSID)) return WifiVerdict.SSID_UNREADABLE;
            if (!cfg.trustedBssids().contains(b)) return WifiVerdict.BSSID_MISMATCH;
        }
        return WifiVerdict.TRUSTED;
    }

    /** Order of checks: mock flag, freshness, accuracy, then distance. */
    public static EligibilityResult evaluateLocation(CampusConfig cfg, LocationSnapshot loc, Instant now) {
        if (loc == null || Double.isNaN(loc.latitude()) || Double.isNaN(loc.longitude())) {
            return EligibilityResult.rejected(Outcome.LOCATION_ERROR, "Location could not be determined.", now);
        }
        if (cfg.rejectMockLocation() && loc.isMock()) {
            return EligibilityResult.rejected(Outcome.MOCK_LOCATION,
                    "A simulated (mock) location was detected and is not accepted.", now);
        }
        if (loc.ageMillis() < 0 || loc.ageMillis() > cfg.maxLocationAgeMillis()) {
            return EligibilityResult.rejected(Outcome.STALE_LOCATION,
                    "The GPS reading is too old. Retry to get a fresh fix.", now);
        }
        double acc = loc.accuracyMeters();
        if (Double.isNaN(acc) || acc <= 0 || acc > cfg.maxAccuracyMeters()) {
            return EligibilityResult.rejected(Outcome.LOW_ACCURACY,
                    String.format(Locale.ROOT, "GPS accuracy is too low (needs %.0f m or better). Indoor GPS is often weaker; join campus Wi-Fi or move outdoors.",
                            cfg.maxAccuracyMeters()), now);
        }
        double dist = GeoMath.distanceMeters(loc.latitude(), loc.longitude(), cfg.latitude(), cfg.longitude());
        if (dist > cfg.radiusMeters()) {
            return EligibilityResult.rejected(Outcome.OUT_OF_RANGE,
                    String.format(Locale.ROOT, "You are outside the %.0f m campus radius.", cfg.radiusMeters()), now);
        }
        return EligibilityResult.eligibleGps(String.format(Locale.ROOT, "GPS:dist=%.1fm;acc=%.1fm", dist, acc), now);
    }
}
