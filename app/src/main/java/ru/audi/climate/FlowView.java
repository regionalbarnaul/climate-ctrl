package ru.audi.climate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

public class FlowView extends View {

    public interface OnPointSet {
        void onPointSet(int index, float xf, float yf);
    }

    private OnPointSet listener;
    private SharedPreferences prefs;
    private Random rnd = new Random();

    // 6 точек:
    // 0 = старт стекло, 1 = старт лицо, 2 = старт ноги
    // 3 = конец стекло, 4 = конец лицо, 5 = конец ноги
    private float[] ptsX = new float[6];
    private float[] ptsY = new float[6];

    // dir 0=стекло, 1=стек+ноги, 2=лицо, 3=ноги
    private static final int[] START_IDX = {0, 0, 1, 2};
    private static final int[] END_IDX   = {3, 3, 4, 5};

    private int fan = 0;
    private int dir = 2;
    private double temperature = 20.0;

    private int calibIndex = -1;

    private static final int N = 400;
    private float[] pT       = new float[N];
    private float[] pOffX    = new float[N];
    private float[] pOffY    = new float[N];
    private float[] pSize    = new float[N];
    private float[] pSpeed   = new float[N];
    private float[] pPhase   = new float[N];
    private float[] pFreq    = new float[N];

    private Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap sprite;
    private Matrix mtx = new Matrix();

    private long lastNano = 0;

    public FlowView(Context c) { super(c); init(c); }
    public FlowView(Context c, AttributeSet a) { super(c, a); init(c); }
    public FlowView(Context c, AttributeSet a, int d) { super(c, a, d); init(c); }

    private void init(Context c) {
        prefs = c.getSharedPreferences("flow3", Context.MODE_PRIVATE);
        createSprite();
        loadPoints();
        for (int i = 0; i < N; i++) {
            resetParticle(i);
            pT[i] = rnd.nextFloat();
        }
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void createSprite() {
        int size = 64;
        sprite = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cc = new Canvas(sprite);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RadialGradient rg = new RadialGradient(
            size / 2f, size / 2f, size / 2f,
            new int[]{0xFFFFFFFF, 0x40FFFFFF, 0x00FFFFFF},
            new float[]{0f, 0.4f, 1f},
            Shader.TileMode.CLAMP);
        p.setShader(rg);
        cc.drawCircle(size / 2f, size / 2f, size / 2f, p);
    }

    private void loadPoints() {
        // 0 старт стекло, 1 старт лицо, 2 старт ноги
        float[][] defStart = {
            {0.42f, 0.62f},
            {0.42f, 0.60f},
            {0.42f, 0.65f}
        };
        // 3 конец стекло, 4 конец лицо, 5 конец ноги
        float[][] defEnd = {
            {0.22f, 0.18f},
            {0.60f, 0.42f},
            {0.55f, 0.92f}
        };
        for (int i = 0; i < 3; i++) {
            ptsX[i]     = prefs.getFloat("x" + i,     defStart[i][0]);
            ptsY[i]     = prefs.getFloat("y" + i,     defStart[i][1]);
            ptsX[i + 3] = prefs.getFloat("x" + (i+3), defEnd[i][0]);
            ptsY[i + 3] = prefs.getFloat("y" + (i+3), defEnd[i][1]);
        }
    }

    private void savePoints() {
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < 6; i++) {
            e.putFloat("x" + i, ptsX[i]);
            e.putFloat("y" + i, ptsY[i]);
        }
        e.apply();
    }

    private void resetParticle(int i) {
        pT[i] = -0.2f * rnd.nextFloat();
        pOffX[i] = (rnd.nextFloat() - 0.5f) * 0.03f;
        pOffY[i] = (rnd.nextFloat() - 0.5f) * 0.03f;
        pSize[i] = 6 + rnd.nextFloat() * 10;
        pSpeed[i] = 0.7f + rnd.nextFloat() * 0.6f;
        pPhase[i] = rnd.nextFloat() * (float)Math.PI * 2f;
        pFreq[i] = 0.5f + rnd.nextFloat() * 1.5f;
    }

    public void setListener(OnPointSet l) { listener = l; }
    public void setFan(int v) { fan = v; }
    public void setDirection(int d) { dir = d; }
    public void setTemperature(double t) { temperature = t; }

    public void startCalib(int index) { calibIndex = index; invalidate(); }
    public void stopCalib() { calibIndex = -1; savePoints(); invalidate(); }
    public void resetPoints() {
        prefs.edit().clear().apply();
        loadPoints();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (calibIndex < 0) return false;
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            float xf = Math.max(0f, Math.min(1f, ev.getX() / getWidth()));
            float yf = Math.max(0f, Math.min(1f, ev.getY() / getHeight()));
            ptsX[calibIndex] = xf;
            ptsY[calibIndex] = yf;
            if (listener != null) listener.onPointSet(calibIndex, xf, yf);
            invalidate();
            return true;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        long now = System.nanoTime();
        float dt = (now - lastNano) / 1_000_000_000f;
        lastNano = now;
        if (dt > 0.1f) dt = 0.1f;
        if (dt < 0f) dt = 0.016f;

        updateParticles(dt);

        if (fan > 0) drawStream(canvas, w, h);
        if (calibIndex >= 0) drawCalibMarkers(canvas, w, h);

        postInvalidateOnAnimation();
    }

    private void updateParticles(float dt) {
        if (fan <= 0) return;
        float baseSpeed = 0.15f + fan * 0.15f;
        for (int i = 0; i < N; i++) {
            pT[i] += baseSpeed * pSpeed[i] * dt;
            if (pT[i] > 1f) resetParticle(i);
        }
    }

    private void drawStream(Canvas canvas, int w, int h) {
        int[] rgb = tempColor(temperature);
        PorterDuffColorFilter cf = new PorterDuffColorFilter(
            Color.rgb(rgb[0], rgb[1], rgb[2]), PorterDuff.Mode.SRC_IN);
        particlePaint.setColorFilter(cf);

        for (int i = 0; i < N; i++) {
            float t = pT[i];
            if (t < 0f) continue;

            int sIdx, eIdx;
            if (dir == 1) {
                // стек+ноги: чётные — стекло, нечётные — ноги
                if (i % 2 == 0) { sIdx = 0; eIdx = 3; }  // стекло
                else            { sIdx = 2; eIdx = 5; }  // ноги
            } else {
                sIdx = START_IDX[dir];
                eIdx = END_IDX[dir];
            }

            float sx = ptsX[sIdx] * w;
            float sy = ptsY[sIdx] * h;
            float ex = ptsX[eIdx] * w;
            float ey = ptsY[eIdx] * h;

            float bx = sx + (ex - sx) * t;
            float by = sy + (ey - sy) * t;

            float turb  = (float)Math.sin(t * pFreq[i] * 6f + pPhase[i]) * t * 0.008f * w;
            float turb2 = (float)Math.cos(t * pFreq[i] * 4f + pPhase[i]) * t * 0.005f * h;

            float px = bx + pOffX[i] * w + turb;
            float py = by + pOffY[i] * h + turb2;

            float scale = 0.6f + t * 1.6f;
            float radius = pSize[i] * scale * (w / 400f);

            float alpha = (float)Math.sin(t * Math.PI) * 0.06f * 255f;
            if (alpha <= 0f) continue;
            if (alpha > 255f) alpha = 255f;

            float s = (radius * 2f) / 64f;
            mtx.reset();
            mtx.setScale(s, s);
            mtx.postTranslate(px - radius, py - radius);

            particlePaint.setAlpha((int)alpha);
            canvas.drawBitmap(sprite, mtx, particlePaint);
        }
        particlePaint.setColorFilter(null);
    }

    private int[] tempColor(double t) {
        if (t <= 22) {
            float k = (float)((t - 16) / 6);
            return new int[]{ (int)(60 + 90*k), (int)(140 + 80*k), 255 };
        } else if (t <= 26) {
            float k = (float)((t - 22) / 4);
            return new int[]{ (int)(150 + 90*k), (int)(220 + 20*k), (int)(255 - 40*k) };
        } else {
            float k = (float)((t - 26) / 4);
            return new int[]{ 240, (int)(240 - 180*k), (int)(215 - 180*k) };
        }
    }

    private void drawCalibMarkers(Canvas canvas, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 6; i++) {
            float x = ptsX[i] * w;
            float y = ptsY[i] * h;
            boolean active = (i == calibIndex);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(active ? 6 : 3);
            p.setColor(active ? 0xFF5CE1E6 : 0x805CE1E6);
            canvas.drawCircle(x, y, active ? 30 : 20, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(active ? 0xFFFFFFFF : 0xFF5CE1E6);
            canvas.drawCircle(x, y, active ? 8 : 4, p);
        }
    }
}
