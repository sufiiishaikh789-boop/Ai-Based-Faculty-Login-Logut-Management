package com.example.facultyattendance.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-secret application settings (campus configuration, time rules, recognition thresholds)
 * plus the administrator password HASH and lock-out counters. Stored in app-private
 * SharedPreferences, which are excluded from Android backup (see AndroidManifest + xml rules).
 * No biometric data and no plaintext secrets are kept here.
 */
public final class SettingsRepository {
    private static final String FILE = "smart_faculty_settings";
    private final SharedPreferences p;

    public SettingsRepository(Context context) {
        p = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- campus ----
    public CampusConfig getCampusConfig() {
        CampusConfig.Builder b = CampusConfig.builder();
        for (String s : split(p.getString("campus.ssids", ""))) b.addSsid(s);
        for (String s : split(p.getString("campus.bssids", ""))) b.addBssid(s);
        b.center(parseDouble(p.getString("campus.lat", "")), parseDouble(p.getString("campus.lon", "")));
        b.radiusMeters(parseDoubleOr(p.getString("campus.radius", ""), CampusConfig.DEFAULT_RADIUS_METERS));
        b.maxAccuracyMeters(parseDoubleOr(p.getString("campus.accuracy", ""), CampusConfig.DEFAULT_MAX_ACCURACY_METERS));
        b.maxLocationAgeMillis(p.getLong("campus.ageMs", CampusConfig.DEFAULT_MAX_LOCATION_AGE_MS));
        b.gpsWaitMillis(p.getLong("campus.gpsWaitMs", CampusConfig.DEFAULT_GPS_WAIT_MS));
        b.rejectMockLocation(p.getBoolean("campus.rejectMock", true));
        return b.build();
    }

    public void saveCampusConfig(CampusConfig c) {
        p.edit()
                .putString("campus.ssids", join(c.trustedSsids()))
                .putString("campus.bssids", join(c.trustedBssids()))
                .putString("campus.lat", c.latitude() == null ? "" : String.valueOf(c.latitude()))
                .putString("campus.lon", c.longitude() == null ? "" : String.valueOf(c.longitude()))
                .putString("campus.radius", String.valueOf(c.radiusMeters()))
                .putString("campus.accuracy", String.valueOf(c.maxAccuracyMeters()))
                .putLong("campus.ageMs", c.maxLocationAgeMillis())
                .putLong("campus.gpsWaitMs", c.gpsWaitMillis())
                .putBoolean("campus.rejectMock", c.rejectMockLocation())
                .apply();
    }

    // ---- attendance policy ----
    public AttendancePolicy getAttendancePolicy() {
        ZoneId zone;
        try {
            zone = ZoneId.of(p.getString("policy.zone", ZoneId.systemDefault().getId()));
        } catch (RuntimeException e) {
            zone = ZoneId.systemDefault();
        }
        return new AttendancePolicy(
                Duration.ofSeconds(Math.max(1, p.getLong("policy.windowSec", AttendancePolicy.DEFAULT_DUPLICATE_WINDOW.getSeconds()))),
                Duration.ofSeconds(Math.max(0, p.getLong("policy.cooldownSec", AttendancePolicy.DEFAULT_RELOGIN_COOLDOWN.getSeconds()))),
                AttendancePolicy.DEFAULT_ELIGIBILITY_AGE, zone);
    }

    public void saveAttendancePolicy(AttendancePolicy a) {
        p.edit()
                .putLong("policy.windowSec", a.duplicateWindow().getSeconds())
                .putLong("policy.cooldownSec", a.reLoginCooldown().getSeconds())
                .putString("policy.zone", a.zone().getId())
                .apply();
    }

    // ---- recognition ----
    public RecognitionPolicy getRecognitionPolicy() {
        RecognitionPolicy d = RecognitionPolicy.defaults();
        try {
            return new RecognitionPolicy(
                    p.getFloat("rec.threshold", d.similarityThreshold()),
                    p.getFloat("rec.margin", d.ambiguityMargin()),
                    p.getInt("rec.consecutive", d.consecutiveMatches()),
                    p.getBoolean("rec.blink", d.requireBlink()),
                    d.enrollmentSamples());
        } catch (IllegalArgumentException e) {
            return d;
        }
    }

    public void saveRecognitionPolicy(RecognitionPolicy r) {
        p.edit()
                .putFloat("rec.threshold", r.similarityThreshold())
                .putFloat("rec.margin", r.ambiguityMargin())
                .putInt("rec.consecutive", r.consecutiveMatches())
                .putBoolean("rec.blink", r.requireBlink())
                .apply();
    }

    // ---- demo switch (only honoured by builds that contain demo support) ----
    public boolean isDemoEnabled() { return p.getBoolean("demo.enabled", false); }
    public void setDemoEnabled(boolean v) { p.edit().putBoolean("demo.enabled", v).apply(); }

    // ---- administrator credentials (hash only) ----
    public String getAdminHash() { return p.getString("admin.hash", null); }
    public void setAdminHash(String h) { p.edit().putString("admin.hash", h).apply(); }
    public int getAdminFailures() { return p.getInt("admin.fail", 0); }
    public long getAdminLockedUntil() { return p.getLong("admin.lockUntil", 0L); }
    public void saveAdminThrottle(int failures, long lockedUntil) {
        p.edit().putInt("admin.fail", failures).putLong("admin.lockUntil", lockedUntil).apply();
    }

    // ---- helpers ----
    private static List<String> split(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null) return out;
        for (String part : s.split("\n")) if (!part.trim().isEmpty()) out.add(part.trim());
        return out;
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append('\n'); sb.append(l.get(i)); }
        return sb.toString();
    }

    private static Double parseDouble(String s) {
        try { return s == null || s.trim().isEmpty() ? null : Double.valueOf(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static double parseDoubleOr(String s, double fallback) {
        Double d = parseDouble(s);
        return d == null ? fallback : d;
    }
}
