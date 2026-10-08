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
    private Path[] paths;
    private float phase = 0f;
    private int fanSpeed = 0;
    private int direction = 2;
    private final Handler h = new Handler(Looper.getMainLooper());

    // 0 = старт (дефлектор торпеды)
    // 1 = стекло (взял с твоего тапа)
    // 2 = лицо   (взял с твоего тапа)
    // 3 = ноги   (прикидка по салону)
    private static final float A_X = 0.38f, A_Y = 0.42f;
    private static final float B_X = 0.47f, B_Y = 0.27f;
    private static final float C_X = 0.61f, C_Y = 0.34f;
    private static final float D_X = 0.42f, D_Y = 0.72f;

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
        paintGlow.setStrokeWidth(16f);
        paintGlow.setAlpha(60);

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
        rebuildPaths();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        rebuildPaths();
    }

    private void rebuildPaths() {
        int w = getWidth(), hh = getHeight();
        if (w == 0 || hh == 0) { paths = null; return; }

        float Ax = A_X * w, Ay = A_Y * hh;
        float Bx = B_X * w, By = B_Y * hh;
        float Cx = C_X * w, Cy = C_Y * hh;
        float Dx = D_X * w, Dy = D_Y * hh;

        java.util.List<Path> list = new java.util.ArrayList<>();

        switch (direction) {
            case 0:
                list.add(line(Ax, Ay, Bx, By));
                break;
            case 1:
                list.add(line(Ax, Ay, Bx, By));
                list.add(line(Ax, Ay, Dx, Dy));
                break;
            case 2:
                list.add(line(Ax, Ay, Cx, Cy));
                break;
            case 3:
                list.add(line(Ax, Ay, Dx, Dy));
                break;
            default:
                list.add(line(Ax, Ay, Cx, Cy));
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
}
