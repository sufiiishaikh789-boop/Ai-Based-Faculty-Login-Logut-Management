package com.example.facultyattendance.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.facultyattendance.R;
import com.example.facultyattendance.config.AttendancePolicy;
import com.example.facultyattendance.config.CampusConfig;
import com.example.facultyattendance.config.RecognitionPolicy;
import com.example.facultyattendance.databinding.ActivityConfigBinding;
import com.example.facultyattendance.location.AndroidLocationSource;
import com.example.facultyattendance.location.LocationFetchResult;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Administrator configuration of campus Wi-Fi/GPS, time rules and recognition thresholds.
 * Nothing here ships with real values: the fields start empty and validation reports exactly
 * what is missing. Coordinates are never written to the audit history or to logs.
 */
public class ConfigActivity extends AdminBaseActivity {
    private ActivityConfigBinding b;
    private boolean loaded;

    private TextInputEditText ssids, bssids, lat, lon, radius, accuracy, ageSec, gpsWaitSec;
    private TextInputEditText windowMin, cooldownSec, zone, threshold, margin, consecutive;
    private MaterialSwitch rejectMock, blink;

    private final ActivityResultLauncher<String[]> gpsPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) fetchGps();
                else toast("Precise location permission is needed to read the GPS position.");
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityConfigBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        buildForm();
        b.btnSaveConfig.setOnClickListener(v -> save());
        b.btnUseGps.setOnClickListener(v -> {
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) fetchGps();
            else gpsPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        });
    }

    @Override
    protected void onAdminReady() {
        if (loaded) return;                    // do not overwrite unsaved edits when the screen resumes
        loaded = true;
        container.io.execute(() -> {
            final CampusConfig c = container.settings.getCampusConfig();
            final AttendancePolicy p = container.settings.getAttendancePolicy();
            final RecognitionPolicy r = container.settings.getRecognitionPolicy();
            ui(() -> populate(c, p, r));
        });
    }

    // ------------------------------------------------------------------ form construction

    private void buildForm() {
        section("Campus Wi-Fi (primary condition)");
        ssids = field("Trusted SSIDs, one per line", "Exact network names of the university Wi-Fi. Leave empty to rely on GPS only.",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);
        bssids = field("Approved access points (BSSID), one per line, optional",
                "aa:bb:cc:dd:ee:ff. When filled in, ONLY these access points count (stronger than SSID alone).",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);

        section("Campus GPS (fallback condition)");
        lat = field("Campus centre latitude", "Decimal degrees, e.g. 12.345678. Leave empty if GPS fallback is not used.", decimal(true), false);
        lon = field("Campus centre longitude", null, decimal(true), false);
        radius = field("Allowed radius (metres)", "Default 10.", decimal(false), false);
        accuracy = field("Maximum accepted GPS accuracy (metres)", "Fixes reporting a worse accuracy are rejected. Default 10.", decimal(false), false);
        ageSec = field("Maximum age of a GPS fix (seconds)", "Default 15.", decimal(false), false);
        gpsWaitSec = field("How long to wait for a GPS fix (seconds)", "3 to 60. Default 15.", decimal(false), false);
        rejectMock = toggle("Reject mock (simulated) locations");

        section("Attendance rules");
        windowMin = field("Duplicate-scan window (minutes)",
                "A second scan inside this window after login is ignored; a scan after it records logout. Default 15.", decimal(false), false);
        cooldownSec = field("Re-login cooldown after logout (seconds)", "0 = the next scan after logout always opens a new session. Default 60.", decimal(false), false);
        zone = field("Time zone (IANA id)", "e.g. Asia/Kolkata. Timestamps are stored in UTC and displayed in this zone.", InputType.TYPE_CLASS_TEXT, false);

        section("Face recognition");
        threshold = field("Similarity threshold (0-1)", "PLACEHOLDER default 0.70. Must be calibrated for YOUR model (docs/MODEL_SETUP.md).", decimal(false), false);
        margin = field("Ambiguity margin (0-0.5)", "Best match must beat the runner-up by this much. Default 0.05.", decimal(false), false);
        consecutive = field("Consecutive matching frames required", "Default 3.", InputType.TYPE_CLASS_NUMBER, false);
        blink = toggle("Require a blink (basic liveness check, not validated anti-spoofing)");
    }

    private static int decimal(boolean signed) {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | (signed ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void section(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextAppearance(R.style.Text_App_Section);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(22);
        b.configForm.addView(tv, lp);
    }

    private TextInputEditText field(String label, @Nullable String helper, int inputType, boolean multiline) {
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(label);
        if (helper != null) til.setHelperText(helper);
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(inputType);
        if (multiline) et.setMinLines(2);
        til.addView(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.configForm.addView(til, lp);
        return et;
    }

    private MaterialSwitch toggle(String label) {
        MaterialSwitch s = new MaterialSwitch(this);
        s.setText(label);
        s.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.configForm.addView(s, lp);
        return s;
    }

    // ------------------------------------------------------------------ data <-> form

    private void populate(CampusConfig c, AttendancePolicy p, RecognitionPolicy r) {
        ssids.setText(join(c.trustedSsids()));
        bssids.setText(join(c.trustedBssids()));
        lat.setText(c.latitude() == null ? "" : String.valueOf(c.latitude()));
        lon.setText(c.longitude() == null ? "" : String.valueOf(c.longitude()));
        radius.setText(num(c.radiusMeters()));
        accuracy.setText(num(c.maxAccuracyMeters()));
        ageSec.setText(num(c.maxLocationAgeMillis() / 1000.0));
        gpsWaitSec.setText(num(c.gpsWaitMillis() / 1000.0));
        rejectMock.setChecked(c.rejectMockLocation());
        windowMin.setText(num(p.duplicateWindow().getSeconds() / 60.0));
        cooldownSec.setText(num(p.reLoginCooldown().getSeconds()));
        zone.setText(p.zone().getId());
        threshold.setText(num(r.similarityThreshold()));
        margin.setText(num(r.ambiguityMargin()));
        consecutive.setText(String.valueOf(r.consecutiveMatches()));
        blink.setChecked(r.requireBlink());
        showStatus(c);
    }

    private void save() {
        final CampusConfig campus;
        final AttendancePolicy policy;
        final RecognitionPolicy recognition;
        try {
            CampusConfig.Builder cb = CampusConfig.builder();
            for (String s : lines(ssids)) cb.addSsid(s);
            for (String s : lines(bssids)) cb.addBssid(s);
            cb.center(optional(lat), optional(lon))
                    .radiusMeters(required(radius))
                    .maxAccuracyMeters(required(accuracy))
                    .maxLocationAgeMillis(Math.round(required(ageSec) * 1000))
                    .gpsWaitMillis(Math.round(required(gpsWaitSec) * 1000))
                    .rejectMockLocation(rejectMock.isChecked());
            campus = cb.build();
            policy = new AttendancePolicy(Duration.ofSeconds(Math.round(required(windowMin) * 60)),
                    Duration.ofSeconds(Math.round(required(cooldownSec))),
                    AttendancePolicy.DEFAULT_ELIGIBILITY_AGE, ZoneId.of(text(zone)));
            recognition = new RecognitionPolicy((float) required(threshold), (float) required(margin),
                    (int) Math.round(required(consecutive)), blink.isChecked(), RecognitionPolicy.DEFAULT_ENROLLMENT_SAMPLES);
        } catch (NumberFormatException e) {
            toast("Please enter a valid number in every numeric field.");
            return;
        } catch (IllegalArgumentException | DateTimeException e) {
            toast("Invalid value: check the time zone, window, threshold and margin.");
            return;
        }
        container.io.execute(() -> {
            container.settings.saveCampusConfig(campus);
            container.settings.saveAttendancePolicy(policy);
            container.settings.saveRecognitionPolicy(recognition);
            container.attendance.recordAudit("CONFIG_CHANGE", "campus_usable=" + campus.isUsable()
                    + "; window_s=" + policy.duplicateWindow().getSeconds(), container.clock.millis());
            ui(() -> { toast("Configuration saved."); showStatus(campus); });
        });
    }

    private void showStatus(CampusConfig c) {
        List<String> problems = c.validate();
        boolean usable = c.isUsable();
        StringBuilder sb = new StringBuilder();
        if (usable) {
            sb.append("Campus configuration is VALID.\nWi-Fi check: ").append(c.isWifiConfigured() ? "configured" : "not used")
                    .append("   GPS fallback: ").append(c.isGpsConfigured() ? "configured" : "not used");
        } else {
            sb.append("Campus configuration is NOT USABLE. Attendance is blocked outside demo mode.");
            for (String p : problems) sb.append("\n• ").append(p);
        }
        sb.append("\n\nDevice-side checks cannot stop determined location or network spoofing; see docs/SECURITY_PRIVACY.md.");
        b.configStatus.setText(sb.toString());
        b.configStatus.setTextColor(ContextCompat.getColor(this, usable ? R.color.state_ok : R.color.state_error));
    }

    // ------------------------------------------------------------------ GPS helper

    private void fetchGps() {
        b.btnUseGps.setEnabled(false);
        container.io.execute(() -> {
            final LocationFetchResult r = new AndroidLocationSource(this, 20_000).fetch();
            ui(() -> {
                b.btnUseGps.setEnabled(true);
                if (r.status() == LocationFetchResult.Status.OK) {
                    lat.setText(String.format(Locale.US, "%.6f", r.snapshot().latitude()));
                    lon.setText(String.format(Locale.US, "%.6f", r.snapshot().longitude()));
                    toast(String.format(Locale.US, "Filled from GPS. Accuracy about %.0f m%s. Stand exactly on the reference point and retry if accuracy is poor.",
                            r.snapshot().accuracyMeters(), r.snapshot().isMock() ? " (MOCK LOCATION DETECTED)" : ""));
                } else {
                    toast("No GPS fix (" + r.status() + "). Go outdoors, enable Location and retry.");
                }
            });
        });
    }

    // ------------------------------------------------------------------ parsing helpers

    private boolean granted(String p) { return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED; }

    private static String text(TextInputEditText t) { return t.getText() == null ? "" : t.getText().toString().trim(); }

    private static double required(TextInputEditText t) { return Double.parseDouble(text(t)); }

    private static Double optional(TextInputEditText t) {
        String s = text(t);
        return s.isEmpty() ? null : Double.valueOf(s);
    }

    private static String[] lines(TextInputEditText t) { return text(t).split("\\r?\\n"); }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append('\n'); sb.append(l.get(i)); }
        return sb.toString();
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
