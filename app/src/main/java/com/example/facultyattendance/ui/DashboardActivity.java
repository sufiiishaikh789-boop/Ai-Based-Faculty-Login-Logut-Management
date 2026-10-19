package com.example.facultyattendance.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.facultyattendance.databinding.ActivityDashboardBinding;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.domain.FacultyRecord;
import com.example.facultyattendance.util.TimeFormat;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Faculty dashboard. Reachable ONLY right after a successful face verification (the UID comes from
 * that verification), so one faculty member cannot browse another's attendance from here.
 */
public class DashboardActivity extends BaseActivity {
    public static final String EXTRA_UID = "uid";

    private ActivityDashboardBinding b;
    private RowAdapter adapter;
    private String uid;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDashboardBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        uid = getIntent().getStringExtra(EXTRA_UID);
        if (uid == null) { finish(); return; }
        adapter = new RowAdapter(null);
        b.recentList.setLayoutManager(new LinearLayoutManager(this));
        b.recentList.setAdapter(adapter);
        b.btnDone.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (uid != null) load();
    }

    private void load() {
        container.io.execute(() -> {
            final ZoneId zone = container.settings.getAttendancePolicy().zone();
            final FacultyRecord f = container.faculty.get(uid);
            final List<AttendanceRecord> recent = container.attendance.recentFor(uid, 10);
            String today = container.clock.instant().atZone(zone).toLocalDate().toString();
            final List<AttendanceRecord> todays = container.attendance.forDate(uid, today);
            ui(() -> render(f, recent, todays, zone));
        });
    }

    private void render(FacultyRecord f, List<AttendanceRecord> recent, List<AttendanceRecord> todays, ZoneId zone) {
        b.facultyName.setText(f == null ? uid : f.name());
        b.facultyMeta.setText(f == null ? "" : f.uid() + (f.department().isEmpty() ? "" : "  ·  " + f.department()));

        boolean anyDemo = false;
        for (AttendanceRecord r : recent) anyDemo |= r.isDemo();
        b.dashDemoBanner.setVisibility(anyDemo ? View.VISIBLE : View.GONE);

        AttendanceRecord latest = recent.isEmpty() ? null : recent.get(0);
        if (latest == null) {
            setBadge("NO SESSION YET", RowAdapter.NEUTRAL);
            b.lastLogin.setText("Latest login: —");
            b.lastLogout.setText("Latest logout: —");
        } else if (latest.isOpen()) {
            setBadge("LOGGED IN · OPEN SESSION", RowAdapter.OK);
            b.lastLogin.setText("Latest login: " + TimeFormat.dateTime(latest.loginAt(), zone));
            b.lastLogout.setText("Latest logout: none yet (session still open)");
        } else {
            setBadge("LOGGED OUT", RowAdapter.NEUTRAL);
            b.lastLogin.setText("Latest login: " + TimeFormat.dateTime(latest.loginAt(), zone));
            b.lastLogout.setText("Latest logout: " + TimeFormat.dateTime(latest.logoutAt(), zone));
        }

        Duration total = Duration.ZERO;
        int open = 0;
        for (AttendanceRecord r : todays) {
            if (r.logoutAt() != null) total = total.plus(Duration.between(r.loginAt(), r.logoutAt())); else open++;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(todays.size()).append(todays.size() == 1 ? " session" : " sessions").append(" today");
        sb.append("\nCompleted time: ").append(total.toHours()).append("h ").append(total.toMinutes() % 60).append("m");
        if (open > 0) sb.append("\nOpen session in progress (no logout recorded yet)");
        b.todaySummary.setText(sb.toString());

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (AttendanceRecord r : recent) {
            String title = TimeFormat.dateTime(r.loginAt(), zone);
            String sub = r.logoutAt() == null ? "Logout: none (open session)" : "Logout: " + TimeFormat.dateTime(r.logoutAt(), zone);
            String badge = r.isOpen() ? "OPEN" : (r.isDemo() ? "DEMO" : "DONE");
            rows.add(new RowAdapter.Row(title, sub, badge, r.isOpen() ? RowAdapter.WARN : (r.isDemo() ? RowAdapter.WARN : RowAdapter.OK), r));
        }
        adapter.submit(rows);
        b.recentEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setBadge(String text, int kind) {
        b.sessionBadge.setText(text);
        int bg = kind == RowAdapter.OK ? com.example.facultyattendance.R.drawable.bg_badge_ok
                : com.example.facultyattendance.R.drawable.bg_badge_neutral;
        b.sessionBadge.setBackgroundResource(bg);
    }
}
