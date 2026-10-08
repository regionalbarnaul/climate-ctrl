package ru.audi.climate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FlowView extends View {

    public interface OnPointSet { void onPointSet(int index, float xf, float yf); }

    private final Paint paintLine;
    private final Paint paintParticle;
    private Path[] paths;
    private PathMeasure[] measures;
    private float[] pathLengths;
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();

    private float phase = 0f;
    private int fanSpeed = 0;
    private int direction = 2;
    private int fogColor = 0xFF5CE1E6;
    private final Handler h = new Handler(Looper.getMainLooper());

    private float[] xf = {0.38f, 0.38f, 0.38f, 0.38f,   0.47f, 0.61f, 0.42f, 0.47f};
    private float[] yf = {0.42f, 0.42f, 0.42f, 0.42f,   0.27f, 0.34f, 0.72f, 0.72f};

    private int calibTarget = -1;
    private OnPointSet listener;
    private final SharedPreferences sp;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateParticles();
            invalidate();
            h.postDelayed(this, 33);
        }
    };

    private static class Particle {
        int pathIndex;
        float t;       // 0..1 вдоль пути
        float speed;   // шаг за кадр
        float size;
    }

    public FlowView(Context c) { this(c, null); }

    public FlowView(Context c, AttributeSet a) {
        super(c, a);
        sp = c.getSharedPreferences("flow", Context.MODE_PRIVATE);
        loadPoints();

        paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeCap(Paint.Cap.ROUND);
        paintLine.setColor(0xFF5CE1E6);

        paintParticle = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintParticle.setStyle(Paint.Style.FILL);
        paintParticle.setColor(0xFF6FF3FF);
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
        paintLine.setColor(fogColor);
        paintParticle.setColor(fogColor);
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

        List<Path> list = new ArrayList<>();
        switch (direction) {
            case 0:
                list.add(line(xf[0]*w, yf[0]*hh, xf[4]*w, yf[4]*hh));
                break;
            case 1:
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

        // Готовим измерения пути для частиц
        measures = new PathMeasure[paths.length];
        pathLengths = new float[paths.length];
        for (int i = 0; i < paths.length; i++) {
            measures[i] = new PathMeasure(paths[i], false);
            pathLengths[i] = measures[i].getLength();
        }
        // Сбрасываем существующие частицы
        particles.clear();
        spawnInitial();
    }

    private Path line(float x1, float y1, float x2, float y2) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        return p;
    }

    private void spawnInitial() {
        if (paths == null) return;
        for (int pi = 0; pi < paths.length; pi++) {
            // 6 частиц на путь
            for (int i = 0; i < 6; i++) {
                Particle p = new Particle();
                p.pathIndex = pi;
                p.t = rng.nextFloat();
                p.speed = 0.008f + rng.nextFloat() * 0.008f;
                p.size = 4f + rng.nextFloat() * 4f;
                particles.add(p);
            }
        }
    }

    private void updateParticles() {
        if (paths == null || fanSpeed == 0) return;
        float mult = 0.5f + fanSpeed * 0.35f;  // скорость от вентилятора
        for (Particle p : particles) {
            p.t += p.speed * mult;
            if (p.t > 1f) {
                p.t = 0f;
                p.speed = 0.008f + rng.nextFloat() * 0.008f;
                p.size = 4f + rng.nextFloat() * 4f;
                p.pathIndex = rng.nextInt(paths.length);
            }
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (paths == null || paths.length == 0 || fanSpeed == 0) return;

        int r = (fogColor >> 16) & 0xFF;
        int g = (fogColor >> 8) & 0xFF;
        int b = fogColor & 0xFF;

        // 1) Мягкая "труба" — 3 слоя линий
        int[] widths  = { 14, 7, 3 };
        int[] alphas  = { 25, 55, 110 };
        float vk = 0.6f + fanSpeed * 0.15f;

        for (int layer = 0; layer < 3; layer++) {
            paintLine.setStrokeWidth(widths[layer] * vk);
            int a = (int)(alphas[layer] * (0.8f + 0.2f * (float)Math.sin(phase)));
            paintLine.setAlpha(Math.min(255, a));
            for (Path p : paths) canvas.drawPath(p, paintLine);
        }
        phase += 0.06f + fanSpeed * 0.03f;
        if (phase > 6.2832f) phase -= 6.2832f;

        // 2) Частицы
        if (measures == null) return;

        float[] pos = new float[2];
        for (Particle p : particles) {
            if (p.pathIndex >= measures.length) continue;
            PathMeasure m = measures[p.pathIndex];
            if (m == null) continue;

            float dist = p.t * pathLengths[p.pathIndex];
            m.getPosTan(dist, pos, null);

            // Прозрачность: появляется на старте, ярко в середине, гаснет к концу
            float fadeIn  = Math.min(1f, p.t * 4f);
            float fadeOut = Math.min(1f, (1f - p.t) * 3f);
            int alpha = (int)(255 * fadeIn * fadeOut * (0.5f + fanSpeed * 0.1f));
            if (alpha > 255) alpha = 255;
            if (alpha < 0) alpha = 0;

            paintParticle.setAlpha(alpha);
            float size = p.size * (0.8f + fanSpeed * 0.08f);
            canvas.drawCircle(pos[0], pos[1], size, paintParticle);
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
