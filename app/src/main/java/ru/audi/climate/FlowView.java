package ru.audi.climate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

public class FlowView extends View {

    private final Paint paintGlow;
    private final Paint paintLine;
    private Path flowPath;
    private float phase = 0f;
    private int fanSpeed = 0;
    private int direction = 2;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            phase -= 1.5f + fanSpeed * 0.8f;
            invalidate();
            h.postDelayed(this, 33);
        }
    };

    public FlowView(Context c) { this(c, null); }

    public FlowView(Context c, AttributeSet a) {
        super(c, a);
        paintGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGlow.setStyle(Paint.Style.STROKE);
        paintGlow.setStrokeCap(Paint.Cap.ROUND);
        paintGlow.setColor(0xFF5CE1E6);
        paintGlow.setStrokeWidth(24f);
        paintGlow.setAlpha(70);

        paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeCap(Paint.Cap.ROUND);
        paintLine.setColor(0xFF6FF3FF);
        paintLine.setStrokeWidth(3f);
    }

    public void setFan(int f) {
        fanSpeed = Math.max(0, Math.min(5, f));
        invalidate();
    }

    public void setDirection(int d) {
        direction = d;
        rebuildPath();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        rebuildPath();
    }

    private void rebuildPath() {
        int w = getWidth(), hh = getHeight();
        if (w == 0 || hh == 0) return;

        float sx = w * 0.32f;
        float sy = hh * 0.60f;
        float len = w * 0.60f;
        float angle;

        switch (direction) {
            case 0:  angle = -55f; break;
            case 1:  angle = -25f; break;
            case 2:  angle = -5f;  break;
            case 3:  angle =  85f; break;
            default: angle = -5f;
        }

        double rad = Math.toRadians(angle);
        float ex = sx + (float)(Math.cos(rad) * len);
        float ey = sy + (float)(Math.sin(rad) * len);

        Path p = new Path();
        p.moveTo(sx, sy);
        p.lineTo(ex, ey);
        flowPath = p;
    }

    private Path makeDash() {
        Path d = new Path();
        d.addRect(0, 0, 16f, 3f, Path.Direction.CW);
        return d;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (flowPath == null || fanSpeed == 0) return;

        paintGlow.setAlpha(40 + fanSpeed * 12);
        paintGlow.setPathEffect(null);
        canvas.drawPath(flowPath, paintGlow);

        paintLine.setPathEffect(new PathDashPathEffect(
            makeDash(), 40f, phase, PathDashPathEffect.Style.ROTATE));
        canvas.drawPath(flowPath, paintLine);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        h.postDelayed(ticker, 33);
    }

    @Override protected void onDetachedFromWindow() {
        h.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }
}
