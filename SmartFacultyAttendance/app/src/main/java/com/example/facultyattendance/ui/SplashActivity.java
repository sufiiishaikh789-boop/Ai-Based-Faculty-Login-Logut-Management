package com.example.facultyattendance.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.example.facultyattendance.databinding.ActivitySplashBinding;

/**
 * Branded splash with a restrained fade/rise animation. While it plays, the recognition model and
 * database are warmed on a background thread. Transition rules:
 *  - always continue to the face-verification screen (it explains any missing setup itself);
 *  - on first run, with no administrator yet, the administrator setup screen is opened on top of it.
 */
public class SplashActivity extends BaseActivity {
    private static final long MIN_SPLASH_MS = 1400;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean minTimeElapsed;
    private boolean warmed;
    private boolean proceeded;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding b = ActivitySplashBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());

        b.logoMark.setAlpha(0f);
        b.logoMark.setTranslationY(24f);
        b.logoMark.animate().alpha(1f).translationY(0f).setDuration(650).start();
        b.brandName.setAlpha(0f);
        b.brandName.animate().alpha(1f).setStartDelay(250).setDuration(600).start();
        b.tagline.setAlpha(0f);
        b.tagline.animate().alpha(1f).setStartDelay(550).setDuration(600).start();

        handler.postDelayed(() -> { minTimeElapsed = true; proceedIfReady(); }, MIN_SPLASH_MS);
        container.io.execute(() -> {
            container.prepareModel();
            container.settings.getCampusConfig();          // touch settings so the first read is off the UI thread
            ui(() -> { warmed = true; proceedIfReady(); });
        });
    }

    private void proceedIfReady() {
        if (proceeded || !minTimeElapsed || !warmed) return;
        proceeded = true;
        Intent verify = new Intent(this, VerifyActivity.class);
        if (!container.adminAuth.isSetUp()) {
            startActivities(new Intent[]{verify, new Intent(this, AdminLoginActivity.class)});
        } else {
            startActivity(verify);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
