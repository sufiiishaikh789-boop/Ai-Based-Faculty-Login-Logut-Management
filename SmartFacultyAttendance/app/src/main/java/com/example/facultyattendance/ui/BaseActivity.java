package com.example.facultyattendance.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.facultyattendance.app.AppContainer;

/** Shared behaviour: edge-to-edge insets (required for targetSdk 35+), container access, safe UI posts. */
public abstract class BaseActivity extends AppCompatActivity {
    protected AppContainer container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        container = AppContainer.get(this);
    }

    /** Sets the content view and keeps it clear of the status/navigation bars and display cutouts. */
    protected void setRoot(View root) {
        setContentView(root);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    protected boolean alive() { return !isFinishing() && !isDestroyed(); }

    /** Runs on the UI thread only if the screen still exists (background callbacks can outlive it). */
    protected void ui(Runnable r) {
        runOnUiThread(() -> { if (alive()) r.run(); });
    }

    protected void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
}
