package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.facultyattendance.ui.AdminLoginActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** UI smoke test: first run asks the administrator to CREATE a password (there is no default one). */
@RunWith(AndroidJUnit4.class)
public class AdminSetupScreenTest {
    @Before public void clearSettings() {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences("smart_faculty_settings", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void firstRunShowsPasswordConfirmationField() {
        try (ActivityScenario<AdminLoginActivity> scenario = ActivityScenario.launch(AdminLoginActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.confirmLayout).getVisibility());
                assertEquals(activity.getString(R.string.btn_create_password),
                        ((android.widget.Button) activity.findViewById(R.id.btnLogin)).getText().toString());
            });
        }
    }
}
