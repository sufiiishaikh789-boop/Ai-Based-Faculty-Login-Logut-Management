package com.example.facultyattendance.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.facultyattendance.R;

/**
 * Face-alignment guide: dimmed screen with an oval cut-out, a state-coloured ring and a restrained
 * scanning line. The animation stops when scanning stops, when the view detaches, and when the
 * user has disabled animations in system settings.
 */
public class ScanOverlayView extends View {
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path scrimPath = new Path();
    private final Path ovalPath = new Path();
    private final RectF oval = new RectF();
    private final float density;
    private ValueAnimator animator;
    private float progress;
    private boolean scanning;

    public ScanOverlayView(Context context) { this(context, null); }

    public ScanOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        scrimPaint.setStyle(Paint.Style.FILL);
        scrimPaint.setColor(ContextCompat.getColor(context, R.color.scrim_dark));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4 * density);
        ringPaint.setColor(ContextCompat.getColor(context, R.color.state_info));
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(ContextCompat.getColor(context, R.color.state_info));
        linePaint.setAlpha(190);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float ovalW = w * 0.72f;
        float ovalH = ovalW * 1.3f;
        float cx = w / 2f;
        float cy = h * 0.38f;
        oval.set(cx - ovalW / 2f, cy - ovalH / 2f, cx + ovalW / 2f, cy + ovalH / 2f);
        scrimPath.reset();
        scrimPath.setFillType(Path.FillType.EVEN_ODD);
        scrimPath.addRect(0, 0, w, h, Path.Direction.CW);
        scrimPath.addOval(oval, Path.Direction.CW);
        ovalPath.reset();
        ovalPath.addOval(oval, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(scrimPath, scrimPaint);
        canvas.drawOval(oval, ringPaint);
        if (scanning && animator != null) {
            canvas.save();
            canvas.clipPath(ovalPath);
            float y = oval.top + progress * oval.height();
            canvas.drawRect(oval.left, y - 2 * density, oval.right, y + 2 * density, linePaint);
            canvas.restore();
        }
    }

    /** Colour the ring/line for the current state (pass a resolved colour, not a resource id). */
    public void setAccent(int color) {
        ringPaint.setColor(color);
        linePaint.setColor(color);
        linePaint.setAlpha(190);
        invalidate();
    }

    public void setScanning(boolean on) {
        if (on == scanning) return;
        scanning = on;
        if (on && ValueAnimator.areAnimatorsEnabled()) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1900);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
            animator.start();
        } else {
            stopAnimator();
        }
        invalidate();
    }

    private void stopAnimator() {
        if (animator != null) { animator.cancel(); animator = null; }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimator();
        super.onDetachedFromWindow();
    }
}
