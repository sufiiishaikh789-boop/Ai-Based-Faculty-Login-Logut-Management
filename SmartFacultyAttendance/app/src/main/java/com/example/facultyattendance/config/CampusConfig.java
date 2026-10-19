package com.example.facultyattendance.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Campus eligibility settings. The shipped defaults are EMPTY PLACEHOLDERS: until an administrator
 * fills them in, validate() reports problems and no attendance can be recorded outside DEMO mode.
 *
 * Wi-Fi is the primary condition (SSID, optionally narrowed by a BSSID allowlist).
 * GPS is the fallback (centre point + radius, default 10 m) with an accuracy requirement.
 */
public final class CampusConfig {
    public static final double DEFAULT_RADIUS_METERS = 10.0;
    public static final double DEFAULT_MAX_ACCURACY_METERS = 10.0;
    public static final long DEFAULT_MAX_LOCATION_AGE_MS = 15_000L;
    public static final long DEFAULT_GPS_WAIT_MS = 15_000L;

    private static final Pattern BSSID = Pattern.compile("([0-9a-f]{2}:){5}[0-9a-f]{2}");
    /** Android returns this placeholder when the real BSSID is hidden from the app. */
    public static final String REDACTED_BSSID = "02:00:00:00:00:00";

    private final List<String> trustedSsids;
    private final List<String> trustedBssids;
    private final Double latitude;
    private final Double longitude;
    private final double radiusMeters;
    private final double maxAccuracyMeters;
    private final long maxLocationAgeMillis;
    private final long gpsWaitMillis;
    private final boolean rejectMockLocation;

    private CampusConfig(Builder b) {
        this.trustedSsids = Collections.unmodifiableList(new ArrayList<String>(b.trustedSsids));
        this.trustedBssids = Collections.unmodifiableList(new ArrayList<String>(b.trustedBssids));
        this.latitude = b.latitude;
        this.longitude = b.longitude;
        this.radiusMeters = b.radiusMeters;
        this.maxAccuracyMeters = b.maxAccuracyMeters;
        this.maxLocationAgeMillis = b.maxLocationAgeMillis;
        this.gpsWaitMillis = b.gpsWaitMillis;
        this.rejectMockLocation = b.rejectMockLocation;
    }

    public static Builder builder() { return new Builder(); }

    /** Placeholder configuration: nothing configured, so nothing is eligible. */
    public static CampusConfig unconfigured() { return builder().build(); }

    public List<String> trustedSsids() { return trustedSsids; }
    public List<String> trustedBssids() { return trustedBssids; }
    public Double latitude() { return latitude; }
    public Double longitude() { return longitude; }
    public double radiusMeters() { return radiusMeters; }
    public double maxAccuracyMeters() { return maxAccuracyMeters; }
    public long maxLocationAgeMillis() { return maxLocationAgeMillis; }
    public long gpsWaitMillis() { return gpsWaitMillis; }
    public boolean rejectMockLocation() { return rejectMockLocation; }

    public boolean isWifiConfigured() { return !trustedSsids.isEmpty(); }

    public boolean isGpsConfigured() {
        return latitude != null && longitude != null && coordinatesPlausible(latitude, longitude);
    }

    private static boolean coordinatesPlausible(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) return false;
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return false;
        // (0,0) is the classic "unset" value, never a real campus.
        return !(lat == 0.0 && lon == 0.0);
    }

    /** Returns human-readable problems. Empty list = the configuration is usable. */
    public List<String> validate() {
        List<String> problems = new ArrayList<String>();
        if (!isWifiConfigured() && (latitude == null || longitude == null)) {
            problems.add("Neither a trusted Wi-Fi SSID nor campus coordinates are configured.");
        }
        for (String s : trustedSsids) {
            if (s.isEmpty() || s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32)
                problems.add("Invalid SSID (must be 1-32 bytes): " + s);
        }
        for (String b : trustedBssids) {
            if (!BSSID.matcher(b).matches() || b.equals(REDACTED_BSSID))
                problems.add("Invalid BSSID (expected aa:bb:cc:dd:ee:ff): " + b);
        }
        if (!trustedBssids.isEmpty() && trustedSsids.isEmpty())
            problems.add("A BSSID allowlist needs at least one trusted SSID as well.");
        if (latitude != null || longitude != null) {
            if (latitude == null || longitude == null || !coordinatesPlausible(latitude, longitude))
                problems.add("Campus coordinates are missing, out of range, or still (0,0).");
        }
        if (!(radiusMeters > 0 && radiusMeters <= 1000))
            problems.add("Radius must be between 0 and 1000 metres.");
        if (!(maxAccuracyMeters > 0 && maxAccuracyMeters <= 100))
            problems.add("Maximum accepted GPS accuracy must be between 0 and 100 metres.");
        if (maxLocationAgeMillis <= 0 || maxLocationAgeMillis > 120_000L)
            problems.add("Maximum location age must be between 1 ms and 120 s.");
        if (gpsWaitMillis < 3_000L || gpsWaitMillis > 60_000L)
            problems.add("GPS wait must be between 3 and 60 seconds.");
        return problems;
    }

    /** True only when validate() is clean AND at least one of Wi-Fi or GPS is actually configured. */
    public boolean isUsable() {
        return validate().isEmpty() && (isWifiConfigured() || isGpsConfigured());
    }

    public static String normalizeSsid(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s;
    }

    public static String normalizeBssid(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static final class Builder {
        private final List<String> trustedSsids = new ArrayList<String>();
        private final List<String> trustedBssids = new ArrayList<String>();
        private Double latitude;
        private Double longitude;
        private double radiusMeters = DEFAULT_RADIUS_METERS;
        private double maxAccuracyMeters = DEFAULT_MAX_ACCURACY_METERS;
        private long maxLocationAgeMillis = DEFAULT_MAX_LOCATION_AGE_MS;
        private long gpsWaitMillis = DEFAULT_GPS_WAIT_MS;
        private boolean rejectMockLocation = true;

        public Builder addSsid(String ssid) {
            String n = normalizeSsid(ssid);
            if (n != null && !n.isEmpty() && !trustedSsids.contains(n)) trustedSsids.add(n);
            return this;
        }
        public Builder addBssid(String bssid) {
            String n = normalizeBssid(bssid);
            if (n != null && !n.isEmpty() && !trustedBssids.contains(n)) trustedBssids.add(n);
            return this;
        }
        public Builder center(Double lat, Double lon) { this.latitude = lat; this.longitude = lon; return this; }
        public Builder radiusMeters(double v) { this.radiusMeters = v; return this; }
        public Builder maxAccuracyMeters(double v) { this.maxAccuracyMeters = v; return this; }
        public Builder maxLocationAgeMillis(long v) { this.maxLocationAgeMillis = v; return this; }
        public Builder gpsWaitMillis(long v) { this.gpsWaitMillis = v; return this; }
        public Builder rejectMockLocation(boolean v) { this.rejectMockLocation = v; return this; }
        public CampusConfig build() { return new CampusConfig(this); }
    }
}
