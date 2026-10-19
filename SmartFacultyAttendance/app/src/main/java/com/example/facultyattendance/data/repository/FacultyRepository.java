package com.example.facultyattendance.data.repository;

import android.database.sqlite.SQLiteConstraintException;
import android.util.Log;

import com.example.facultyattendance.authentication.AdminAuthManager;
import com.example.facultyattendance.authentication.KeystoreTemplateCipher;
import com.example.facultyattendance.authentication.TemplateCodec;
import com.example.facultyattendance.camera.FaceMatcher;
import com.example.facultyattendance.data.local.AppDatabase;
import com.example.facultyattendance.data.local.AuditEntity;
import com.example.facultyattendance.data.local.FacultyEntity;
import com.example.facultyattendance.domain.FacultyRecord;

import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Faculty management and biometric template storage. Every administrative change writes an audit
 * entry in the same transaction. Face templates are encrypted before they reach the database and
 * are NEVER logged. All methods block: call them from a background executor.
 */
public final class FacultyRepository {
    private static final String TAG = "FacultyRepository";

    private final AppDatabase db;
    private final KeystoreTemplateCipher cipher;
    private final Clock clock;

    public FacultyRepository(AppDatabase db, KeystoreTemplateCipher cipher, Clock clock) {
        this.db = db;
        this.cipher = cipher;
        this.clock = clock;
    }

    public List<FacultyRecord> list() {
        List<FacultyRecord> out = new ArrayList<FacultyRecord>();
        for (FacultyEntity e : db.facultyDao().all()) out.add(Mappers.toDomain(e));
        return out;
    }

    public FacultyRecord get(String uid) { return Mappers.toDomain(db.facultyDao().get(uid)); }

    public OpResult create(String uid, String name, String department) {
        String err = validate(uid, name, department);
        if (err != null) return OpResult.fail(err);
        final FacultyEntity e = new FacultyEntity();
        e.uid = uid.trim();
        e.name = name.trim();
        e.department = department == null ? "" : department.trim();
        e.active = true;
        e.createdAtMillis = e.updatedAtMillis = clock.millis();
        try {
            return db.runInTransaction(new Callable<OpResult>() {
                @Override public OpResult call() {
                    db.facultyDao().insert(e);
                    audit("CREATE_FACULTY", e.uid, null, "profile created");
                    return OpResult.ok("Faculty profile created.");
                }
            });
        } catch (SQLiteConstraintException dup) {
            return OpResult.fail("A faculty member with this UID already exists.");
        } catch (RuntimeException ex) {
            Log.e(TAG, "create failed: " + ex.getClass().getSimpleName());
            return OpResult.fail("Database error. Nothing was saved.");
        }
    }

    public OpResult update(final String uid, final String name, final String department, final boolean active) {
        String err = validate(uid, name, department);
        if (err != null) return OpResult.fail(err);
        try {
            return db.runInTransaction(new Callable<OpResult>() {
                @Override public OpResult call() {
                    FacultyEntity e = db.facultyDao().get(uid);
                    if (e == null) return OpResult.fail("Faculty member not found.");
                    boolean wasActive = e.active;
                    e.name = name.trim();
                    e.department = department == null ? "" : department.trim();
                    e.active = active;
                    e.updatedAtMillis = clock.millis();
                    db.facultyDao().update(e);
                    audit(wasActive != active ? (active ? "ACTIVATE_FACULTY" : "DEACTIVATE_FACULTY") : "EDIT_FACULTY",
                            uid, null, "profile updated");
                    return OpResult.ok("Saved.");
                }
            });
        } catch (RuntimeException ex) {
            Log.e(TAG, "update failed: " + ex.getClass().getSimpleName());
            return OpResult.fail("Database error. Nothing was saved.");
        }
    }

    /**
     * Stores an encrypted template. Refuses to store a face that already matches a DIFFERENT
     * active faculty member, which prevents accidental duplicate profiles for one person.
     */
    public OpResult saveTemplate(final String uid, final float[] template, final String modelId,
                                 final boolean demo, final float duplicateThreshold) {
        if (template == null || template.length == 0 || modelId == null) return OpResult.fail("Invalid template.");
        try {
            return db.runInTransaction(new Callable<OpResult>() {
                @Override public OpResult call() throws Exception {
                    FacultyEntity e = db.facultyDao().get(uid);
                    if (e == null) return OpResult.fail("Faculty member not found.");
                    if (!e.active) return OpResult.fail("Activate the profile before enrolling a face.");

                    List<FaceMatcher.Candidate> others = new ArrayList<FaceMatcher.Candidate>();
                    for (FacultyEntity o : db.facultyDao().activeWithTemplate(modelId)) {
                        if (o.uid.equals(uid) || o.encryptedTemplate == null) continue;
                        try {
                            others.add(new FaceMatcher.Candidate(o.uid,
                                    TemplateCodec.toFloats(cipher.decrypt(o.encryptedTemplate, o.uid))));
                        } catch (GeneralSecurityException | IllegalArgumentException skip) {
                            Log.w(TAG, "skipping unreadable template during duplicate check");
                        }
                    }
                    FaceMatcher.MatchResult dup = FaceMatcher.match(template, others, duplicateThreshold, 0f);
                    if (dup.status == FaceMatcher.Status.MATCH) {
                        return OpResult.fail("This face already matches faculty ID " + dup.uid
                                + ". One person must not have two profiles.");
                    }
                    e.encryptedTemplate = cipher.encrypt(TemplateCodec.toBytes(template), uid);
                    e.templateModelId = modelId;
                    e.templateDemo = demo;
                    e.enrolledAtMillis = clock.millis();
                    e.updatedAtMillis = e.enrolledAtMillis;
                    db.facultyDao().update(e);
                    audit("ENROLL_FACE", uid, null, demo ? "template stored (DEMO embedder)" : "template stored");
                    return OpResult.ok("Face enrolled.");
                }
            });
        } catch (RuntimeException ex) {
            Log.e(TAG, "saveTemplate failed: " + ex.getClass().getSimpleName());
            return OpResult.fail("Could not store the template securely. Nothing was saved.");
        }
    }

    public OpResult removeTemplate(final String uid) {
        try {
            return db.runInTransaction(new Callable<OpResult>() {
                @Override public OpResult call() {
                    FacultyEntity e = db.facultyDao().get(uid);
                    if (e == null) return OpResult.fail("Faculty member not found.");
                    e.encryptedTemplate = null;
                    e.templateModelId = null;
                    e.templateDemo = false;
                    e.enrolledAtMillis = 0;
                    e.updatedAtMillis = clock.millis();
                    db.facultyDao().update(e);
                    audit("REMOVE_TEMPLATE", uid, null, "face template deleted");
                    return OpResult.ok("Face template removed.");
                }
            });
        } catch (RuntimeException ex) {
            return OpResult.fail("Database error. Nothing was changed.");
        }
    }

    /** Decrypts active templates enrolled with the given model, for matching during a scan. */
    public List<FaceMatcher.Candidate> loadCandidates(String modelId) {
        List<FaceMatcher.Candidate> out = new ArrayList<FaceMatcher.Candidate>();
        for (FacultyEntity e : db.facultyDao().activeWithTemplate(modelId)) {
            if (e.encryptedTemplate == null) continue;
            try {
                out.add(new FaceMatcher.Candidate(e.uid,
                        TemplateCodec.toFloats(cipher.decrypt(e.encryptedTemplate, e.uid))));
            } catch (GeneralSecurityException | IllegalArgumentException ex) {
                Log.w(TAG, "a stored template could not be decrypted and was skipped");
            }
        }
        return out;
    }

    /** Privacy "panic button": removes every stored face template, then deletes the Keystore key itself. */
    public OpResult eraseAllTemplates() {
        try {
            OpResult r = db.runInTransaction(new Callable<OpResult>() {
                @Override public OpResult call() {
                    int n = 0;
                    for (FacultyEntity e : db.facultyDao().all()) {
                        if (e.encryptedTemplate == null) continue;
                        e.encryptedTemplate = null;
                        e.templateModelId = null;
                        e.templateDemo = false;
                        e.enrolledAtMillis = 0;
                        e.updatedAtMillis = clock.millis();
                        db.facultyDao().update(e);
                        n++;
                    }
                    audit("ERASE_ALL_TEMPLATES", null, null, "templates_removed=" + n);
                    return OpResult.ok("Erased " + n + " face templates and the encryption key.");
                }
            });
            cipher.deleteKey();
            return r;
        } catch (RuntimeException | GeneralSecurityException ex) {
            return OpResult.fail("Could not erase everything. Try again.");
        }
    }

    /** Debug/demo cleanup: removes DEMO attendance and demo-enrolled profiles. Real data is untouched. */
    public OpResult resetDemoData() {
        try {
            return db.runInTransaction(new Callable<OpResult>() {
                @Override public OpResult call() {
                    int a = db.attendanceDao().deleteDemo();
                    int f = db.facultyDao().deleteDemoFacultyWithoutAttendance();
                    audit("RESET_DEMO_DATA", null, null, "attendance_removed=" + a + "; faculty_removed=" + f);
                    return OpResult.ok("Removed " + a + " demo attendance rows and " + f + " demo profiles.");
                }
            });
        } catch (RuntimeException ex) {
            return OpResult.fail("Database error. Nothing was changed.");
        }
    }

    private void audit(String action, String uid, String reason, String details) {
        AuditEntity a = new AuditEntity();
        a.action = action;
        a.targetUid = uid;
        a.atMillis = clock.millis();
        a.actor = AdminAuthManager.ACTOR;
        a.reason = reason;
        a.details = details;
        db.auditDao().insert(a);
    }

    private static String validate(String uid, String name, String department) {
        if (!FacultyRecord.isValidUid(uid == null ? null : uid.trim()))
            return "UID must be 3-32 characters: letters, digits, dot, dash or underscore.";
        if (name == null || name.trim().length() < 2 || name.trim().length() > 80)
            return "Name must be 2-80 characters.";
        if (department != null && department.trim().length() > 80)
            return "Department must be at most 80 characters.";
        return null;
    }
}
