package com.example.facultyattendance.ui;

import android.content.Intent;

/**
 * Base for every administrator screen. On each resume it re-checks the in-memory admin session
 * (5-minute idle timeout). Without a valid session the user is sent to the sign-in screen,
 * so administrator functions can never be reached through ordinary faculty use.
 */
public abstract class AdminBaseActivity extends BaseActivity {
    /** Called on every resume while the session is valid. Load or refresh data here. */
    protected abstract void onAdminReady();

    @Override
    protected void onResume() {
        super.onResume();
        if (!container.adminAuth.isSessionActive()) {
            startActivity(new Intent(this, AdminLoginActivity.class));
            finish();
            return;
        }
        onAdminReady();
    }
}
