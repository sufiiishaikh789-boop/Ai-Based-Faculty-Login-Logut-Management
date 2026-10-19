package com.example.facultyattendance.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.example.facultyattendance.data.repository.OpResult;
import com.example.facultyattendance.databinding.ActivityFacultyEditBinding;
import com.example.facultyattendance.domain.FacultyRecord;
import com.example.facultyattendance.util.TimeFormat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.ZoneId;

/** Add / edit / deactivate a faculty profile, enrol a face, or delete the stored face template. */
public class FacultyEditActivity extends AdminBaseActivity {
    public static final String EXTRA_UID = "uid";

    private ActivityFacultyEditBinding b;
    private String existingUid;            // null when creating a new profile
    private FacultyRecord current;

    private final ActivityResultLauncher<Intent> enrollLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> loadProfile());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityFacultyEditBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        existingUid = getIntent().getStringExtra(EXTRA_UID);
        b.btnSave.setOnClickListener(v -> save());
        b.btnEnroll.setOnClickListener(v -> confirmEnrollment());
        b.btnRemoveTemplate.setOnClickListener(v -> confirmRemoveTemplate());
        renderFaceSection();
    }

    @Override protected void onAdminReady() { if (existingUid != null) loadProfile(); }

    private void loadProfile() {
        if (existingUid == null) return;
        container.io.execute(() -> {
            final FacultyRecord f = container.faculty.get(existingUid);
            ui(() -> {
                current = f;
                if (f == null) { toast("Profile not found."); finish(); return; }
                b.inputUid.setText(f.uid());
                b.inputUid.setEnabled(false);              // UID is the permanent key; it cannot be edited
                b.inputName.setText(f.name());
                b.inputDept.setText(f.department());
                b.switchActive.setChecked(f.isActive());
                renderFaceSection();
            });
        });
    }

    private void renderFaceSection() {
        boolean saved = current != null;
        b.btnEnroll.setEnabled(saved && current.isActive());
        b.btnRemoveTemplate.setEnabled(saved && current.hasTemplate());
        if (!saved) {
            b.faceStatus.setText("Save the profile first, then enrol a face.");
        } else if (!current.isActive()) {
            b.faceStatus.setText("Profile is deactivated: it cannot be enrolled or verified.");
        } else if (current.hasTemplate()) {
            ZoneId zone = container.settings.getAttendancePolicy().zone();
            b.faceStatus.setText("Face enrolled on " + TimeFormat.dateTime(current.enrolledAt(), zone)
                    + (current.isTemplateDemo() ? "  (DEMO template: not real recognition)" : ""));
        } else {
            b.faceStatus.setText("No face enrolled yet.");
        }
    }

    private void save() {
        final String uid = text(b.inputUid), name = text(b.inputName), dept = text(b.inputDept);
        final boolean active = b.switchActive.isChecked();
        b.btnSave.setEnabled(false);
        container.io.execute(() -> {
            final OpResult r = existingUid == null
                    ? container.faculty.create(uid, name, dept)
                    : container.faculty.update(existingUid, name, dept, active);
            ui(() -> {
                b.btnSave.setEnabled(true);
                toast(r.message);
                if (r.ok) {
                    existingUid = uid.trim();
                    loadProfile();
                }
            });
        });
    }

    /** Explains the purpose and storage of biometric data BEFORE any face is captured. */
    private void confirmEnrollment() {
        if (current == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Biometric data notice")
                .setMessage("Purpose: to recognise this faculty member for automatic attendance.\n\n"
                        + "What is stored: a numeric face template (not a photo). Camera frames are analysed "
                        + "in memory and are not saved.\n\n"
                        + "Where: encrypted on this device only, with a key held in the Android Keystore. "
                        + "Nothing is sent over a network.\n\n"
                        + "Control: the template can be deleted at any time here, and deactivating the profile "
                        + "stops it being used.\n\n"
                        + "Continue only if the faculty member has been informed and agrees.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Faculty agrees: start", (d, w) -> {
                    Intent i = new Intent(this, VerifyActivity.class);
                    i.putExtra(VerifyActivity.EXTRA_ENROLL_UID, current.uid());
                    enrollLauncher.launch(i);
                })
                .show();
    }

    private void confirmRemoveTemplate() {
        if (current == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove face template?")
                .setMessage("The stored template for " + current.name() + " is deleted. They will not be recognised until re-enrolled. Attendance history is kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> container.io.execute(() -> {
                    final OpResult r = container.faculty.removeTemplate(current.uid());
                    ui(() -> { toast(r.message); loadProfile(); });
                }))
                .show();
    }

    private static String text(com.google.android.material.textfield.TextInputEditText t) {
        return t.getText() == null ? "" : t.getText().toString().trim();
    }
}
