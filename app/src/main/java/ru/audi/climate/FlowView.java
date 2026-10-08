package ru.audi.climate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
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

    private final Paint paintBlob;
    private final Paint paintParticle;
    private Bitmap softBlob;
    private PathMeasure[] measures;
    private float[] pathLengths;
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();
    private float phase = 0f;
    private int fanSpeed = 0;
    private int direction = 2;
    private int fogColor = 0xFF5CE1E6;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Matrix matrix = new Matrix();

    private float[] xf = {0.38f, 0.38f, 0.38f, 0.38f,   0.47f, 0.61f, 0.42f, 0.47f};
    private float[] yf = {0.42f, 0.42f, 0.42f, 0.42f,   0.27f, 0.34f, 0.72f, 0.72f};

    private int calibTarget = -1;
    private OnPointSet listener;
    private final SharedPreferences sp;

    private static final int BLOB_SIZE = 128;   // спрайт
    private static final int PUFFS_PER_PATH = 20; // сколько пухов вдоль линии

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateParticles();
            phase += 0.05f;
            if (phase > 6.2832f) phase -= 6.2832f;
            invalidate();
            h.postDelayed(this, 33);
        }
    };

    private static class Particle {
        int pathIndex;
        float t;
        float speed;
        float size;
        float wobbleSeed;
    }

    public FlowView(Context c) { this(c, null); }

    public FlowView(Context c, AttributeSet a) {
        super(c, a);
        sp = c.getSharedPreferences("flow", Context.MODE_PRIVATE);
        loadPoints();

        paintBlob = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBlob.setFilterBitmap(true);

        paintParticle = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintParticle.setStyle(Paint.Style.FILL);

        softBlob = makeSoftBlob();
    }

    // Размытый мягкий шар — рисуется один раз
    private Bitmap makeSoftBlob() {
        int s = BLOB_SIZE;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RadialGradient grad = new RadialGradient(
            s / 2f, s / 2f, s / 2f,
            new int[]{ 0xFFFFFFFF, 0xAAFFFFFF, 0x00FFFFFF },
            new float[]{ 0f, 0.35f, 1f },
            Shader.TileMode.CLAMP);
        p.setShader(grad);
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        return bmp;
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
        if (w == 0 || hh == 0) { measures = null; return; }

        List<float[]> lines = new ArrayList<>();

        switch (direction) {
            case 0:
                lines.add(new float[]{xf[0]*w, yf[0]*hh, xf[4]*w, yf[4]*hh});
                break;
            case 1:
                lines.add(new float[]{xf[0]*w, yf[0]*hh, xf[4]*w, yf[4]*hh});
                lines.add(new float[]{xf[2]*w, yf[2]*hh, xf[6]*w, yf[6]*hh});
                break;
            case 2:
                lines.add(new float[]{xf[1]*w, yf[1]*hh, xf[5]*w, yf[5]*hh});
                break;
            case 3:
                lines.add(new float[]{xf[2]*w, yf[2]*hh, xf[6]*w, yf[6]*hh});
                break;
            default:
                lines.add(new float[]{xf[1]*w, yf[1]*hh, xf[5]*w, yf[5]*hh});
        }

        measures = new PathMeasure[lines.size()];
        pathLengths = new float[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            android.graphics.Path p = new android.graphics.Path();
            float[] l = lines.get(i);
            p.moveTo(l[0], l[1]);
            p.lineTo(l[2], l[3]);
            measures[i] = new PathMeasure(p, false);
            pathLengths[i] = measures[i].getLength();
        }

        particles.clear();
        spawnInitial();
    }

    private void spawnInitial() {
        if (measures == null) return;
        for (int pi = 0; pi < measures.length; pi++) {
            int count = 10 + fanSpeed * 4;
            for (int i = 0; i < count; i++) {
                Particle p = new Particle();
                p.pathIndex = pi;
                p.t = rng.nextFloat();
                p.speed = 0.008f + rng.nextFloat() * 0.008f;
                p.size = 4f + rng.nextFloat() * 5f;
                p.wobbleSeed = rng.nextFloat() * 6.28f;
                particles.add(p);
            }
        }
    }

    private void updateParticles() {
        if (measures == null || fanSpeed == 0) return;
        float mult = 0.4f + fanSpeed * 0.35f;
        for (Particle p : particles) {
            p.t += p.speed * mult;
            if (p.t > 1f) {
                p.t = 0f;
                p.speed = 0.008f + rng.nextFloat() * 0.008f;
                p.size = 4f + rng.nextFloat() * 5f;
                p.wobbleSeed = rng.nextFloat() * 6.28f;
                if (measures.length > 0) p.pathIndex = rng.nextInt(measures.length);
            }
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (measures == null || measures.length == 0 || fanSpeed == 0) return;

        // Красим спрайт в актуальный цвет и задаём общий уровень альфы
        paintBlob.setColorFilter(new PorterDuffColorFilter(fogColor, PorterDuff.Mode.SRC_IN));
        paintParticle.setColor(fogColor);

        float pulse = 0.9f + 0.1f * (float) Math.sin(phase);
        float[] pos = new float[2];
        float[] tan = new float[2];

        // ========== 1) Облако из мягких пухов ==========
        for (int pi = 0; pi < measures.length; pi++) {
            PathMeasure m = measures[pi];
            float len = pathLengths[pi];
            for (int i = 0; i < PUFFS_PER_PATH; i++) {
                float t = i / (float)(PUFFS_PER_PATH - 1);
                m.getPosTan(t * len, pos, tan);

                // Покачивание поперёк — «живое» облако
                float nx = -tan[1], ny = tan[0]; // перпендикуляр
                float wobble = (float)Math.sin(phase * 1.7f + t * 4.5f + pi * 2.1f) * 6f;
                float px = pos[0] + nx * wobble;
                float py = pos[1] + ny * wobble;

                // Размер пуха растёт от старта к концу
                float radius = (16f + t * 42f) * (1f + fanSpeed * 0.10f);

                // Альфа: 0 у старта → пик в середине → 0 у конца
                float a = (float)Math.sin(t * Math.PI);
                a *= (0.35f + 0.12f * fanSpeed) * pulse;
                int alpha = (int)(a * 255);
                if (alpha > 220) alpha = 220;
                if (alpha < 0) alpha = 0;
                paintBlob.setAlpha(alpha);

                // Масштаб спрайта под нужный радиус
                float scale = radius * 2f / BLOB_SIZE;
                matrix.reset();
                matrix.postTranslate(-BLOB_SIZE / 2f, -BLOB_SIZE / 2f);
                matrix.postScale(scale, scale);
                matrix.postTranslate(px, py);
                canvas.drawBitmap(softBlob, matrix, paintBlob);
            }
        }

        // ========== 2) Летящие частицы ==========
        for (Particle p : particles) {
            if (p.pathIndex >= measures.length) continue;
            PathMeasure m = measures[p.pathIndex];
            float dist = p.t * pathLengths[p.pathIndex];
            m.getPosTan(dist, pos, tan);

            float nx = -tan[1], ny = tan[0];
            float wobble = (float)Math.sin(phase * 2.2f + p.wobbleSeed) * 8f;
            float px = pos[0] + nx * wobble;
            float py = pos[1] + ny * wobble;

            float fadeIn  = Math.min(1f, p.t * 5f);
            float fadeOut = Math.min(1f, (1f - p.t) * 4f);
            int a = (int)(255 * fadeIn * fadeOut * (0.5f + fanSpeed * 0.1f));
            if (a > 255) a = 255;
            if (a < 0) a = 0;

            paintParticle.setAlpha(a);
            float size = p.size * (0.8f + fanSpeed * 0.06f) * pulse;
            canvas.drawCircle(px, py, size, paintParticle);
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
