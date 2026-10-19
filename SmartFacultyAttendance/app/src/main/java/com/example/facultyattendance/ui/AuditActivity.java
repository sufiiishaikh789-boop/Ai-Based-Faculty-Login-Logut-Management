package com.example.facultyattendance.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.facultyattendance.R;
import com.example.facultyattendance.databinding.ActivityListBinding;
import com.example.facultyattendance.domain.AuditEntry;
import com.example.facultyattendance.util.TimeFormat;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class AuditActivity extends AdminBaseActivity {
    private ActivityListBinding b;
    private RowAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        b.listTitle.setText(R.string.audit_title);
        adapter = new RowAdapter(null);
        b.listView.setLayoutManager(new LinearLayoutManager(this));
        b.listView.setAdapter(adapter);
    }

    @Override
    protected void onAdminReady() {
        container.io.execute(() -> {
            final List<AuditEntry> entries = container.attendance.recentAudit(300);
            final ZoneId zone = container.settings.getAttendancePolicy().zone();
            ui(() -> {
                List<RowAdapter.Row> rows = new ArrayList<>();
                for (AuditEntry e : entries) {
                    StringBuilder sub = new StringBuilder(TimeFormat.dateTime(e.at(), zone));
                    if (e.targetUid() != null) sub.append("  ·  ").append(e.targetUid());
                    if (e.recordId() != null) sub.append("  ·  record #").append(e.recordId());
                    if (e.reason() != null && !e.reason().isEmpty()) sub.append("\nReason: ").append(e.reason());
                    if (e.details() != null && !e.details().isEmpty()) sub.append("\n").append(e.details());
                    rows.add(new RowAdapter.Row(e.action(), sub.toString(), e.actor(), RowAdapter.NEUTRAL, e));
                }
                adapter.submit(rows);
                b.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
}
