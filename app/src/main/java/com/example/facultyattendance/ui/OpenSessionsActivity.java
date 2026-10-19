package com.example.facultyattendance.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.facultyattendance.R;
import com.example.facultyattendance.attendance.AdminCorrectionService;
import com.example.facultyattendance.authentication.AdminAuthManager;
import com.example.facultyattendance.databinding.ActivityListBinding;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.util.TimeFormat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists sessions that never got a logout and lets an administrator close one. The administrator
 * picks the logout time (the app never invents one), writes a reason, sees the exact date and
 * time that will be saved, and the correction is recorded in the audit history.
 */
public class OpenSessionsActivity extends AdminBaseActivity {
    private ActivityListBinding b;
    private RowAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        b.listTitle.setText(R.string.open_sessions_title);
        b.listSubtitle.setText(R.string.open_sessions_hint);
        b.listSubtitle.setVisibility(View.VISIBLE);
        adapter = new RowAdapter(row -> pickLogoutTime((AttendanceRecord) row.tag));
        b.listView.setLayoutManager(new LinearLayoutManager(this));
        b.listView.setAdapter(adapter);
    }

    @Override protected void onAdminReady() { reload(); }

    private void reload() {
        container.io.execute(() -> {
            final List<AttendanceRecord> open = container.attendance.openSessions();
            final ZoneId zone = container.settings.getAttendancePolicy().zone();
            final Instant now = container.clock.instant();
            ui(() -> {
                List<RowAdapter.Row> rows = new ArrayList<>();
                for (AttendanceRecord r : open) {
                    Duration age = Duration.between(r.loginAt(), now);
                    rows.add(new RowAdapter.Row(r.facultyName() + "  (" + r.facultyUid() + ")",
                            "Login " + TimeFormat.dateTime(r.loginAt(), zone) + "  ·  open for "
                                    + age.toHours() + "h " + (age.toMinutes() % 60) + "m",
                            r.isDemo() ? "DEMO · OPEN" : "OPEN_SESSION", RowAdapter.WARN, r));
                }
                adapter.submit(rows);
                b.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void pickLogoutTime(final AttendanceRecord rec) {
        final ZoneId zone = container.settings.getAttendancePolicy().zone();
        LocalDateTime login = rec.loginAt().atZone(zone).toLocalDateTime();
        final MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(login.getHour()).setMinute(login.getMinute())
                .setTitleText("Logout time (local)").build();
        picker.addOnPositiveButtonClickListener(v -> {
            LocalDate day = LocalDate.parse(rec.localDate());
            Instant chosen = day.atTime(picker.getHour(), picker.getMinute()).atZone(zone).toInstant();
            if (chosen.isBefore(rec.loginAt())) chosen = day.plusDays(1).atTime(picker.getHour(), picker.getMinute()).atZone(zone).toInstant();
            askReason(rec, chosen, zone);
        });
        picker.show(getSupportFragmentManager(), "logoutTime");
    }

    private void askReason(final AttendanceRecord rec, final Instant logoutAt, ZoneId zone) {
        final EditText input = new EditText(this);
        input.setHint("Reason (required, 5+ characters)");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Close open session?")
                .setMessage(rec.facultyName() + " (" + rec.facultyUid() + ")\nLogout will be saved as:\n"
                        + TimeFormat.dateTime(logoutAt, zone) + "\n\nThis is recorded as an administrator correction.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close session", (d, w) -> {
                    final String reason = input.getText().toString();
                    container.io.execute(() -> {
                        final AdminCorrectionService.Result r = container.correctionService()
                                .closeOpenSession(rec.id(), logoutAt, reason, AdminAuthManager.ACTOR);
                        ui(() -> { toast(r.message); reload(); });
                    });
                })
                .show();
    }
}
