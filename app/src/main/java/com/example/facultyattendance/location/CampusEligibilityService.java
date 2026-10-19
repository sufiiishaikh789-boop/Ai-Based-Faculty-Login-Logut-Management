package com.example.facultyattendance.location;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.facultyattendance.config.CampusConfig;
import com.example.facultyattendance.config.SettingsRepository;
import com.example.facultyattendance.domain.EligibilityResult;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

/** Runs the campus check on a background thread and delivers the result on the main thread. */
public final class CampusEligibilityService {
    public interface Callback { void onResult(EligibilityResult result); }

    private final Context context;
    private final SettingsRepository settings;
    private final Clock clock;
    private final ExecutorService executor;
    private final Handler main = new Handler(Looper.getMainLooper());

    public CampusEligibilityService(Context context, SettingsRepository settings, Clock clock, ExecutorService executor) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.clock = clock;
        this.executor = executor;
    }

    public void check(final boolean demoMode, final Callback callback) {
        executor.execute(new Runnable() {
            @Override public void run() {
                CampusConfig cfg = settings.getCampusConfig();
                WifiSnapshot wifi = new AndroidWifiReader(context).read();
                LocationSource gps = new AndroidLocationSource(context, cfg.gpsWaitMillis());
                final EligibilityResult r = CampusPolicyEvaluator.evaluate(cfg, wifi, gps, clock, demoMode);
                main.post(new Runnable() { @Override public void run() { callback.onResult(r); } });
            }
        });
    }
}
