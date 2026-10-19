package com.example.facultyattendance.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import com.example.facultyattendance.R;
import com.example.facultyattendance.camera.DemoSupport;
import com.example.facultyattendance.config.AttendancePolicy;
import com.example.facultyattendance.config.CampusConfig;
import com.example.facultyattendance.config.RecognitionPolicy;
import com.example.facultyattendance.data.repository.OpResult;
import com.example.facultyattendance.databinding.ActivitySettingsBinding;
import com.example.facultyattendance.util.ModeResolver;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

/** Diagnostics (read-only facts) plus the few destructive maintenance actions, all admin-only. */
public class SettingsActivity extends AdminBaseActivity {
    private ActivitySettingsBinding b;
    private boolean demoSwitchBound;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        b.btnRefresh.setOnClickListener(v -> refresh());
        b.btnResetDemo.setOnClickListener(v -> confirm("Reset demo data?",
                "Deletes attendance rows flagged DEMO and profiles enrolled with the demo embedder. Real data is not touched.",
                () -> container.faculty.resetDemoData()));
        b.btnEraseTemplates.setOnClickListener(v -> confirm("Erase ALL face templates?",
                "Every stored face template and the encryption key are deleted. Nobody will be recognised until re-enrolled. Attendance history is kept.",
                () -> container.faculty.eraseAllTemplates()));
        boolean demoAvailable = DemoSupport.isAvailable();
        b.switchDemo.setVisibility(demoAvailable ? View.VISIBLE : View.GONE);
        b.btnResetDemo.setVisibility(demoAvailable ? View.VISIBLE : View.GONE);
    }

    @Override protected void onAdminReady() { refresh(); }

    private void confirm(String title, String message, final java.util.concurrent.Callable<OpResult> action) {
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (d, w) -> container.io.execute(() -> {
                    OpResult r;
                    try { r = action.call(); } catch (Exception e) { r = OpResult.fail("Action failed."); }
                    final OpResult result = r;
                    ui(() -> { toast(result.message); refresh(); });
                })).show();
    }

    private void refresh() {
        container.io.execute(() -> {
            final ModeResolver.Decision d = container.mode();
            final String report = buildReport();
            ui(() -> render(d, report));
        });
    }

    private void render(ModeResolver.Decision d, String report) {
        b.modeBadge.setText("MODE: " + d.mode);
        int bg = d.mode == ModeResolver.Mode.CONFIGURED ? R.drawable.bg_badge_ok
                : d.mode == ModeResolver.Mode.DEMO ? R.drawable.bg_badge_warn : R.drawable.bg_badge_error;
        b.modeBadge.setBackgroundResource(bg);
        if (d.mode == ModeResolver.Mode.DEMO) {
            b.modeBlockers.setText("DEMO: attendance records are flagged DEMO. This is NOT real identity verification.");
        } else if (d.blockers.isEmpty()) {
            b.modeBlockers.setText("Ready for real attendance.");
        } else {
            StringBuilder sb = new StringBuilder("Blocked until fixed:");
            for (String s : d.blockers) sb.append("\n• ").append(s);
            b.modeBlockers.setText(sb.toString());
        }
        b.diagBody.setText(report);

        if (DemoSupport.isAvailable() && !demoSwitchBound) {
            demoSwitchBound = true;
            b.switchDemo.setChecked(container.settings.isDemoEnabled());
            b.switchDemo.setOnCheckedChangeListener((v, checked) -> { container.settings.setDemoEnabled(checked); refresh(); });
        }
    }

    /** Runs on a background thread (touches the database). Contains no coordinates, SSIDs or personal data. */
    private String buildReport() {
        StringBuilder s = new StringBuilder();
        CampusConfig campus = container.settings.getCampusConfig();
        AttendancePolicy policy = container.settings.getAttendancePolicy();
        RecognitionPolicy rec = container.settings.getRecognitionPolicy();

        s.append("APP\n  version        : ").append(versionName()).append('\n');
        s.append("  demo support   : ").append(DemoSupport.isAvailable() ? "compiled in (debug build)" : "not present (release build)").append("\n\n");

        s.append("PERMISSIONS / SENSORS\n");
        s.append("  camera         : ").append(yn(granted(Manifest.permission.CAMERA))).append('\n');
        s.append("  precise loc.   : ").append(yn(granted(Manifest.permission.ACCESS_FINE_LOCATION))).append('\n');
        s.append("  approx. loc.   : ").append(yn(granted(Manifest.permission.ACCESS_COARSE_LOCATION))).append('\n');
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        s.append("  location on    : ").append(yn(lm != null && LocationManagerCompat.isLocationEnabled(lm))).append('\n');
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        s.append("  wi-fi on       : ").append(yn(wm != null && wm.isWifiEnabled())).append('\n');
        s.append("  front camera   : ").append(yn(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT))).append('\n');
        s.append("  auto time      : ").append(yn(Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 0) == 1))
                .append("  (OFF means the device clock can be changed by hand)\n\n");

        s.append("RECOGNITION\n  model          : ").append(container.realEmbedder.readinessMessage()).append('\n');
        s.append(String.format(Locale.ROOT, "  threshold      : %.2f  margin %.2f  frames %d  blink %s%n%n",
                rec.similarityThreshold(), rec.ambiguityMargin(), rec.consecutiveMatches(), rec.requireBlink() ? "required" : "off"));

        s.append("CAMPUS CONFIGURATION\n");
        List<String> problems = campus.validate();
        s.append("  usable         : ").append(yn(campus.isUsable())).append('\n');
        s.append("  wi-fi / gps    : ").append(campus.isWifiConfigured() ? "wifi " : "").append(campus.isGpsConfigured() ? "gps" : "").append('\n');
        for (String p : problems) s.append("  ! ").append(p).append('\n');
        s.append('\n');

        s.append("ATTENDANCE RULES\n  window         : ").append(policy.duplicateWindow().toMinutes()).append(" min\n");
        s.append("  re-login wait  : ").append(policy.reLoginCooldown().getSeconds()).append(" s\n");
        s.append("  time zone      : ").append(policy.zone().getId()).append("\n\n");

        s.append("DATABASE\n");
        try {
            s.append("  schema version : ").append(container.db.getOpenHelper().getReadableDatabase().getVersion()).append('\n');
            s.append("  faculty        : ").append(container.faculty.list().size()).append('\n');
            s.append("  attendance rows: ").append(container.attendance.totalRecords()).append('\n');
        } catch (RuntimeException e) {
            s.append("  ERROR reading database (").append(e.getClass().getSimpleName()).append(")\n");
        }
        return s.toString();
    }

    private boolean granted(String p) { return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED; }

    private static String yn(boolean v) { return v ? "yes" : "NO"; }

    @SuppressWarnings("deprecation")
    private String versionName() {
        try {
            PackageInfo info = Build.VERSION.SDK_INT >= 33
                    ? getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0))
                    : getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
