package com.example.facultyattendance.app;

import android.content.Context;
import android.util.Log;

import com.example.facultyattendance.attendance.AdminCorrectionService;
import com.example.facultyattendance.attendance.AttendanceService;
import com.example.facultyattendance.authentication.AdminAuthManager;
import com.example.facultyattendance.authentication.KeystoreTemplateCipher;
import com.example.facultyattendance.camera.DemoSupport;
import com.example.facultyattendance.camera.FaceEmbedder;
import com.example.facultyattendance.camera.TfliteFaceEmbedder;
import com.example.facultyattendance.config.SettingsRepository;
import com.example.facultyattendance.data.local.AppDatabase;
import com.example.facultyattendance.data.repository.AttendanceRepository;
import com.example.facultyattendance.data.repository.FacultyRepository;
import com.example.facultyattendance.data.repository.RoomAttendanceStore;
import com.example.facultyattendance.location.CampusEligibilityService;
import com.example.facultyattendance.util.ModeResolver;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Tiny hand-written service locator (no DI framework, easy to read for students).
 * Heavy objects (database, recognition model) are created once and shared, never per frame or per screen.
 */
public final class AppContainer {
    private static volatile AppContainer instance;

    public static AppContainer get(Context context) {
        AppContainer local = instance;
        if (local == null) {
            synchronized (AppContainer.class) {
                local = instance;
                if (local == null) instance = local = new AppContainer(context.getApplicationContext());
            }
        }
        return local;
    }

    public final Clock clock = Clock.systemUTC();
    /** Background pool for database and file work. Never use the main thread for these. */
    public final ExecutorService io = Executors.newFixedThreadPool(2);

    public final SettingsRepository settings;
    public final AppDatabase db;
    public final KeystoreTemplateCipher cipher = new KeystoreTemplateCipher();
    public final FacultyRepository faculty;
    public final AttendanceRepository attendance;
    public final RoomAttendanceStore store;
    public final AdminAuthManager adminAuth;
    public final CampusEligibilityService campus;
    public final TfliteFaceEmbedder realEmbedder;
    private FaceEmbedder demoEmbedder;

    private AppContainer(Context app) {
        settings = new SettingsRepository(app);
        db = AppDatabase.create(app);
        store = new RoomAttendanceStore(db);
        faculty = new FacultyRepository(db, cipher, clock);
        attendance = new AttendanceRepository(db);
        adminAuth = new AdminAuthManager(settings, clock);
        campus = new CampusEligibilityService(app, settings, clock, io);
        realEmbedder = new TfliteFaceEmbedder(app);
    }

    /** Loads the recognition model once (blocking IO: background thread only). */
    public void prepareModel() { realEmbedder.prepare(); }

    /** Resolves DEMO / CONFIGURED / NOT_READY. Call on a background thread after prepareModel(). */
    public ModeResolver.Decision mode() {
        prepareModel();
        return ModeResolver.resolve(DemoSupport.isAvailable(), settings.isDemoEnabled(),
                realEmbedder.isReady(), settings.getCampusConfig().isUsable());
    }

    /** The embedder to use for the current mode: demo embedder only in DEMO mode and only if no real model exists. */
    public synchronized FaceEmbedder activeEmbedder(ModeResolver.Mode mode) {
        if (mode == ModeResolver.Mode.DEMO && !realEmbedder.isReady() && DemoSupport.isAvailable()) {
            if (demoEmbedder == null) demoEmbedder = DemoSupport.createEmbedder();
            return demoEmbedder;
        }
        return realEmbedder;
    }

    /** Built per call so edited policy values apply immediately. Demo records are allowed ONLY in DEMO mode. */
    public AttendanceService attendanceService(ModeResolver.Mode mode) {
        return new AttendanceService(store, clock, settings.getAttendancePolicy(),
                mode == ModeResolver.Mode.DEMO, new Consumer<Throwable>() {
                    @Override public void accept(Throwable t) {
                        Log.e("Attendance", "store failure: " + t.getClass().getSimpleName());   // no details/PII
                    }
                });
    }

    public AdminCorrectionService correctionService() { return new AdminCorrectionService(store, clock); }
}
