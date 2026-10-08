package ru.audi.climate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class FlowView extends View {

    public interface OnPointSet { void onPointSet(int index, float xf, float yf); }

    private final Paint paintFog;
    private Path[] paths;
    private float phase = 0f;
    private int fanSpeed = 0;
    private int direction = 2;
    private int fogColor = 0xFF5CE1E6;
    private final Handler h = new Handler(Looper.getMainLooper());

    // 0..3 — старты для 4 направлений, 4..7 — концы
    private float[] xf = {0.38f, 0.38f, 0.38f, 0.38f,   0.47f, 0.61f, 0.42f, 0.47f};
    private float[] yf = {0.42f, 0.42f, 0.42f, 0.42f,   0.27f, 0.34f, 0.72f, 0.72f};

    private int calibTarget = -1;
    private OnPointSet listener;
    private final SharedPreferences sp;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            phase += 0.04f + fanSpeed * 0.015f;
            if (phase > 6.2832f) phase -= 6.2832f;
            invalidate();
            h.postDelayed(this, 50);
        }
    };

    public FlowView(Context c) { this(c, null); }

    public FlowView(Context c, AttributeSet a) {
        super(c, a);
        sp = c.getSharedPreferences("flow", Context.MODE_PRIVATE);
        loadPoints();

        paintFog = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFog.setStyle(Paint.Style.STROKE);
        paintFog.setStrokeCap(Paint.Cap.ROUND);
        paintFog.setStrokeJoin(Paint.Join.ROUND);
        paintFog.setColor(0xFF5CE1E6);
    }

    private void loadPoints() {
        for (int i = 0; i < 8; i++) {
            xf[i] = sp.getFloat("x" + i, xf[i]);
            yf[i] = sp.getFloat("y" + i, yf[i]);
        }
    }

    private void savePoints() {
        SharedPreferences.Editor e = sp.edit();
        for (int i = 0; i < 8; i++) {
            e.putFloat("x" + i, xf[i]);
            e.putFloat("y" + i, yf[i]);
        }
        e.apply();
    }

    public void setListener(OnPointSet l) { this.listener = l; }

    public void startCalib(int which) { calibTarget = which; }
    public void stopCalib() { calibTarget = -1; savePoints(); }

    public void resetPoints() {
        xf = new float[]{0.38f, 0.38f, 0.38f, 0.38f, 0.47f, 0.61f, 0.42f, 0.47f};
        yf = new float[]{0.42f, 0.42f, 0.42f, 0.42f, 0.27f, 0.34f, 0.72f, 0.72f};
        savePoints();
        rebuildPaths();
        invalidate();
    }

    public void setFan(int f) {
        fanSpeed = Math.max(0, Math.min(5, f));
        invalidate();
    }

    public void setTemperature(double temp) {
        double t = Math.max(16, Math.min(30, temp));
        int r, g, b;
        if (t <= 23) {
            double k = (t - 16) / 7.0;
            r = (int)Math.round(74  + (220 - 74) * k);
            g = (int)Math.round(158 + (235 - 158) * k);
            b = 255;
        } else {
            double k = (t - 23) / 7.0;
            r = (int)Math.round(220 + (255 - 220) * k);
            g = (int)Math.round(235 + (90 - 235) * k);
            b = (int)Math.round(255 + (90 - 255) * k);
        }
        fogColor = 0xFF000000 | (r << 16) | (g << 8) | b;
        invalidate();
    }

    public void setDirection(int d) {
        direction = d;
        rebuildPaths();
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (calibTarget < 0) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            float nx = e.getX() / getWidth();
            float ny = e.getY() / getHeight();
            if (nx < 0) nx = 0; if (nx > 1) nx = 1;
            if (ny < 0) ny = 0; if (ny > 1) ny = 1;
            xf[calibTarget] = nx;
            yf[calibTarget] = ny;
            if (listener != null) listener.onPointSet(calibTarget, nx, ny);
            rebuildPaths();
            invalidate();
            return true;
        }
        return true;
    }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        rebuildPaths();
    }

    private void rebuildPaths() {
        int w = getWidth(), hh = getHeight();
        if (w == 0 || hh == 0) { paths = null; return; }

        java.util.List<Path> list = new java.util.ArrayList<>();

        switch (direction) {
            case 0:
                list.add(line(xf[0]*w, yf[0]*hh, xf[4]*w, yf[4]*hh));
                break;
            case 1:
                // стек+ноги — включаем обе готовые линии
                list.add(line(xf[0]*w, yf[0]*hh, xf[4]*w, yf[4]*hh));
                list.add(line(xf[2]*w, yf[2]*hh, xf[6]*w, yf[6]*hh));
                break;
            case 2:
                list.add(line(xf[1]*w, yf[1]*hh, xf[5]*w, yf[5]*hh));
                break;
            case 3:
                list.add(line(xf[2]*w, yf[2]*hh, xf[6]*w, yf[6]*hh));
                break;
            default:
                list.add(line(xf[1]*w, yf[1]*hh, xf[5]*w, yf[5]*hh));
        }
        paths = list.toArray(new Path[0]);
    }

    private Path line(float x1, float y1, float x2, float y2) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        return p;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (paths == null || paths.length == 0 || fanSpeed == 0) return;

        paintFog.setColor(fogColor);

        float pulse = 0.78f + 0.22f * (float) Math.sin(phase);
        float baseAlpha = (12f + fanSpeed * 10f) * pulse;

        paintFog.setStrokeWidth(90f);
        paintFog.setAlpha(clamp(baseAlpha * 0.45f));
        for (Path p : paths) canvas.drawPath(p, paintFog);

        paintFog.setStrokeWidth(52f);
        paintFog.setAlpha(clamp(baseAlpha * 0.85f));
        for (Path p : paths) canvas.drawPath(p, paintFog);

        paintFog.setStrokeWidth(26f);
        paintFog.setAlpha(clamp(baseAlpha * 1.4f));
        for (Path p : paths) canvas.drawPath(p, paintFog);

        paintFog.setStrokeWidth(8f);
        paintFog.setAlpha(clamp(baseAlpha * 2.1f));
        for (Path p : paths) canvas.drawPath(p, paintFog);
    }

    private int clamp(float a) {
        int v = (int) a;
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        h.postDelayed(ticker, 50);
    }

    @Override protected void onDetachedFromWindow() {
        h.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }
}
