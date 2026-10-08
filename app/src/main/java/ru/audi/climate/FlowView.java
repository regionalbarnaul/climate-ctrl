package ru.audi.climate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

public class FlowView extends View {

    private final Paint paintGlow;
    private final Paint paintLine;
    private Path[] paths;
    private float phase = 0f;
    private int fanSpeed = 0;
    private int direction = 2;
    private final Handler h = new Handler(Looper.getMainLooper());

    // 4 якоря в долях от размера (0..1). Верх=0, низ=1.
    // A — старт (дефлектор), B — в стекло, C — в лицо, D — в ноги.
    private float ax = 0.35f, ay = 0.55f;
    private float bx = 0.50f, by = 0.10f;
    private float cx = 0.60f, cy = 0.35f;
    private float dx = 0.35f, dy = 0.85f;

    // Отладочные флаги — какие якоря уже заданы
    private int anchorStage = 0;  // 0=A, 1=B, 2=C, 3=D, 4=reset

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
        rebuildPaths();
        invalidate();
    }

    // Настройка якорей тапом: A → B → C → D → снова A
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            float xf = e.getX() / getWidth();
            float yf = e.getY() / getHeight();

            String name;
            switch (anchorStage) {
                case 0: ax = xf; ay = yf; name = "A старт"; break;
                case 1: bx = xf; by = yf; name = "B стекло"; break;
                case 2: cx = xf; cy = yf; name = "C лицо"; break;
                case 3: dx = xf; dy = yf; name = "D ноги"; break;
                default: name = "?"; break;
            }
            Toast.makeText(getContext(),
                name + ": x=" + fmt(xf) + " y=" + fmt(yf),
                Toast.LENGTH_SHORT).show();

            anchorStage = (anchorStage + 1) % 4;
            rebuildPaths();
            invalidate();
            return true;
        }
        return super.onTouchEvent(e);
    }

    private String fmt(float f) { return String.format("%.2f", f); }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        rebuildPaths();
    }

    private void rebuildPaths() {
        int w = getWidth(), hh = getHeight();
        if (w == 0 || hh == 0) { paths = null; return; }

        float Ax = ax * w, Ay = ay * hh;
        float Bx = bx * w, By = by * hh;
        float Cx = cx * w, Cy = cy * hh;
        float Dx = dx * w, Dy = dy * hh;

        java.util.List<Path> list = new java.util.ArrayList<>();

        switch (direction) {
            case 0:  // стекло
                list.add(line(Ax, Ay, Bx, By));
                break;
            case 1:  // стекло + ноги — ДВЕ линии
                list.add(line(Ax, Ay, Bx, By));
                list.add(line(Ax, Ay, Dx, Dy));
                break;
            case 2:  // лицо
                list.add(line(Ax, Ay, Cx, Cy));
                break;
            case 3:  // ноги
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

        paintGlow.setAlpha(40 + fanSpeed * 12);
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
