package com.example.facultyattendance.app;

import android.app.Application;

/** Application class. Intentionally light: the container is created lazily on first use. */
public class FacultyAttendanceApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Warm the container on a background thread so the first screen does not pay for Room/Keystore setup.
        final AppContainer container = AppContainer.get(this);
        container.io.execute(new Runnable() {
            @Override public void run() { container.prepareModel(); }
        });
    }
}
