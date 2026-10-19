package com.example.facultyattendance.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.example.facultyattendance.databinding.ActivityAdminHomeBinding;

public class AdminHomeActivity extends AdminBaseActivity {
    private ActivityAdminHomeBinding b;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAdminHomeBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        b.btnFaculty.setOnClickListener(v -> open(FacultyListActivity.class));
        b.btnOpenSessions.setOnClickListener(v -> open(OpenSessionsActivity.class));
        b.btnHistory.setOnClickListener(v -> open(HistoryActivity.class));
        b.btnAudit.setOnClickListener(v -> open(AuditActivity.class));
        b.btnConfig.setOnClickListener(v -> open(ConfigActivity.class));
        b.btnSettings.setOnClickListener(v -> open(SettingsActivity.class));
        b.btnSignOut.setOnClickListener(v -> { container.adminAuth.logout(); finish(); });
    }

    @Override
    protected void onAdminReady() {
        container.io.execute(() -> {
            final int faculty = container.faculty.list().size();
            final int open = container.attendance.openSessions().size();
            final boolean campusOk = container.settings.getCampusConfig().isUsable();
            ui(() -> b.adminSummary.setText(faculty + " faculty profiles  ·  " + open + " open sessions\nCampus configuration: "
                    + (campusOk ? "valid" : "MISSING OR INVALID (attendance is blocked outside demo mode)")));
        });
    }

    private void open(Class<?> target) { startActivity(new Intent(this, target)); }
}
