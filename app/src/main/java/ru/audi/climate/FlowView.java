package ru.audi.climate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class FlowView extends View {

    public interface OnPointSet { void onPointSet(int index, float xf, float yf); }

    private final Paint paintGlow;
    private final Paint paintLine;
    private Path[] paths;
    private float phase = 0f;
    private int fanSpeed = 0;
    private int direction = 2;
    private final Handler h = new Handler(Looper.getMainLooper());

    // 0 старт, 1 стекло, 2 лицо, 3 ноги
    private float[] xf = {0.38f, 0.47f, 0.61f, 0.42f};
    private float[] yf = {0.42f, 0.27f, 0.34f, 0.72f};

    private int calibTarget = -1;
    private OnPointSet listener;
    private final SharedPreferences sp;

    public FlowView(Context c) { this(c, null); }

    public FlowView(Context c, AttributeSet a) {
        super(c, a);
        sp = c.getSharedPreferences("flow", Context.MODE_PRIVATE);
        loadPoints();

        paintGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGlow.setStyle(Paint.Style.STROKE);
        paintGlow.setStrokeCap(Paint.Cap.ROUND);
        paintGlow.setColor(0xFF5CE1E6);
        paintGlow.setStrokeWidth(16f);
        paintGlow.setAlpha(60);

        paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeCap(Paint.Cap.ROUND);
        paintLine.setColor(0xFF6FF3FF);
        paintLine.setStrokeWidth(3f);
    }

    private void loadPoints() {
        for (int i = 0; i < 4; i++) {
            xf[i] = sp.getFloat("x" + i, xf[i]);
            yf[i] = sp.getFloat("y" + i, yf[i]);
        }
    }

    private void savePoints() {
        SharedPreferences.Editor e = sp.edit();
        for (int i = 0; i < 4; i++) {
            e.putFloat("x" + i, xf[i]);
            e.putFloat("y" + i, yf[i]);
        }
        e.apply();
    }

    public void setListener(OnPointSet l) { this.listener = l; }
    public int getCalibTarget() { return calibTarget; }

    public void startCalib(int which) { calibTarget = which; }
    public void stopCalib() { calibTarget = -1; savePoints(); }

    public void resetPoints() {
        xf = new float[]{0.38f, 0.47f, 0.61f, 0.42f};
        yf = new float[]{0.42f, 0.27f, 0.34f, 0.72f};
        savePoints();
        rebuildPaths();
        invalidate();
    }

    public void setFan(int f) {
        fanSpeed = Math.max(0, Math.min(5, f));
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

        float Ax = xf[0] * w, Ay = yf[0] * hh;
        float Bx = xf[1] * w, By = yf[1] * hh;
        float Cx = xf[2] * w, Cy = yf[2] * hh;
        float Dx = xf[3] * w, Dy = yf[3] * hh;

        java.util.List<Path> list = new java.util.ArrayList<>();

        switch (direction) {
            case 0: list.add(line(Ax, Ay, Bx, By)); break;
            case 1:
                list.add(line(Ax, Ay, Bx, By));
                list.add(line(Ax, Ay, Dx, Dy));
                break;
            case 2: list.add(line(Ax, Ay, Cx, Cy)); break;
            case 3: list.add(line(Ax, Ay, Dx, Dy)); break;
            default: list.add(line(Ax, Ay, Cx, Cy));
        }
        paths = list.toArray(new Path[0]);
    }

    private Path line(float x1, float y1, float x2, float y2) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        return p;
    }

    private Path makeDash() {
        Path d = new Path();
        d.addRect(0, 0, 16f, 3f, Path.Direction.CW);
        return d;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (paths == null || paths.length == 0 || fanSpeed == 0) return;

        paintGlow.setAlpha(30 + fanSpeed * 12);
        paintGlow.setPathEffect(null);
        paintLine.setPathEffect(new PathDashPathEffect(
            makeDash(), 40f, phase, PathDashPathEffect.Style.ROTATE));

        for (Path p : paths) {
            canvas.drawPath(p, paintGlow);
            canvas.drawPath(p, paintLine);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        h.postDelayed(ticker, 33);
    }

    @Override protected void onDetachedFromWindow() {
        h.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            phase -= 1.5f + fanSpeed * 0.8f;
            invalidate();
            h.postDelayed(this, 33);
        }
    };
}
