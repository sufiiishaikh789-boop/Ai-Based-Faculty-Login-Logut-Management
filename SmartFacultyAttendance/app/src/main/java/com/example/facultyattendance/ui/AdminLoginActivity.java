package com.example.facultyattendance.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.facultyattendance.R;
import com.example.facultyattendance.authentication.AdminAuthManager;
import com.example.facultyattendance.authentication.PasswordHasher;
import com.example.facultyattendance.databinding.ActivityAdminLoginBinding;

import java.util.Arrays;

/** First run: create the administrator password (no default exists). Afterwards: sign in. */
public class AdminLoginActivity extends BaseActivity {
    private ActivityAdminLoginBinding b;
    private boolean setupMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAdminLoginBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());

        setupMode = !container.adminAuth.isSetUp();
        if (!setupMode && container.adminAuth.isSessionActive()) { goHome(); return; }

        b.loginTitle.setText(setupMode ? R.string.admin_setup_title : R.string.admin_login_title);
        b.loginHint.setText(setupMode ? R.string.admin_setup_hint : R.string.admin_login_hint);
        b.btnLogin.setText(setupMode ? R.string.btn_create_password : R.string.btn_sign_in);
        b.confirmLayout.setVisibility(setupMode ? View.VISIBLE : View.GONE);
        b.btnBack.setOnClickListener(v -> finish());
        b.btnLogin.setOnClickListener(v -> submit());
    }

    @Override
    protected void onStop() {
        if (b != null) {                           // never leave a typed password sitting in the view
            b.passwordInput.setText("");
            b.confirmInput.setText("");
        }
        super.onStop();
    }

    private void submit() {
        final char[] password = toChars(b.passwordInput.getText());
        showError(null);
        if (setupMode) {
            char[] confirm = toChars(b.confirmInput.getText());
            boolean same = Arrays.equals(password, confirm);
            Arrays.fill(confirm, '\0');
            if (!same) { Arrays.fill(password, '\0'); showError("The two passwords do not match."); return; }
            if (!PasswordHasher.isAcceptable(password)) {
                Arrays.fill(password, '\0');
                showError("Use at least 10 characters with some variety.");
                return;
            }
        }
        setBusy(true);
        container.io.execute(() -> {                // PBKDF2 is deliberately slow: keep it off the UI thread
            if (setupMode) {
                final boolean ok = container.adminAuth.setUp(password);
                ui(() -> { setBusy(false); if (ok) goHome(); else showError("Setup failed. Try a different password."); });
            } else {
                final AdminAuthManager.LoginResult r = container.adminAuth.login(password);
                ui(() -> {
                    setBusy(false);
                    switch (r.outcome) {
                        case SUCCESS: goHome(); break;
                        case LOCKED:
                            showError("Too many attempts. Try again in " + Math.max(1, (r.lockRemainingMillis + 999) / 1000) + " s.");
                            break;
                        default:
                            showError("Incorrect password.");
                            break;
                    }
                });
            }
        });
    }

    private void goHome() {
        startActivity(new Intent(this, AdminHomeActivity.class));
        finish();
    }

    private void setBusy(boolean busy) {
        b.btnLogin.setEnabled(!busy);
    }

    private void showError(@Nullable String msg) {
        b.loginError.setText(msg);
        b.loginError.setVisibility(msg == null ? View.GONE : View.VISIBLE);
    }

    private static char[] toChars(@Nullable Editable e) {
        if (e == null) return new char[0];
        char[] out = new char[e.length()];
        e.getChars(0, e.length(), out, 0);
        return out;
    }
}
