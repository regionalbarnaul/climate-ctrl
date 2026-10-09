package ru.audi.climate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class DirButton extends View {

    private int dirIndex = 0;
    private boolean active = false;

    private int accent = 0xFF5CE1E6;
    private int bg = 0xFF131C2C;
    private int bgActive = 0xFF1A3A3D;
    private int borderNormal = 0x335CE1E6;
    private int iconNormal = 0xCCFFFFFF;
    private int iconActive = 0xFF5CE1E6;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DirButton(Context c) { this(c, null); }

    public DirButton(Context c, AttributeSet a) {
        super(c, a);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.5f));
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(2.2f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        setClickable(true);
    }

    public void setDirIndex(int i) { dirIndex = i; invalidate(); }
    public int getDirIndex() { return dirIndex; }
    public void setActive(boolean a) { active = a; invalidate(); }
    public boolean isActive() { return active; }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float r = dp(14);

        // Фон
        bgPaint.setColor(active ? bgActive : bg);
        RectF rect = new RectF(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, bgPaint);

        // Рамка
        borderPaint.setColor(active ? accent : borderNormal);
        float sw = borderPaint.getStrokeWidth();
        RectF inner = new RectF(sw/2, sw/2, w - sw/2, h - sw/2);
        canvas.drawRoundRect(inner, r, r, borderPaint);

        // Иконка
        iconPaint.setColor(active ? iconActive : iconNormal);
        drawIcon(canvas, w, h);
    }

    private void drawIcon(Canvas canvas, float w, float h) {
        float cx = w / 2f, cy = h / 2f;
        float size = Math.min(w, h) * 0.55f;
        float len = size * 0.85f;
        switch (dirIndex) {
            case 0: drawUp(canvas, cx, cy, len, 3); break;
            case 1: drawUpDown(canvas, cx, cy, len); break;
            case 2: drawFace(canvas, cx, cy, len); break;
            case 3: drawDown(canvas, cx, cy, len, 3); break;
        }
    }

    private void arrow(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1, y1, x2, y2, iconPaint);
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float)Math.hypot(dx, dy);
        if (len < 0.001f) return;
        float ux = dx / len, uy = dy / len;
        float head = dp(4);
        float hx1 = x2 - ux*head - uy*head*0.5f;
        float hy1 = y2 - uy*head + ux*head*0.5f;
        float hx2 = x2 - ux*head + uy*head*0.5f;
        float hy2 = y2 - uy*head - ux*head*0.5f;
        Path p = new Path();
        p.moveTo(hx1, hy1);
        p.lineTo(x2, y2);
        p.lineTo(hx2, hy2);
        canvas.drawPath(p, iconPaint);
    }

    private void drawUp(Canvas canvas, float cx, float cy, float len, int count) {
        float gap = dp(6);
        float y1 = cy + len/2;
        float y2 = cy - len/2;
        for (int i = 0; i < count; i++) {
            float x = cx + (i - (count-1)/2f) * gap;
            arrow(canvas, x, y1, x, y2);
        }
    }

    private void drawDown(Canvas canvas, float cx, float cy, float len, int count) {
        float gap = dp(6);
        float y1 = cy - len/2;
        float y2 = cy + len/2;
        for (int i = 0; i < count; i++) {
            float x = cx + (i - (count-1)/2f) * gap;
            arrow(canvas, x, y1, x, y2);
        }
    }

    private void drawUpDown(Canvas canvas, float cx, float cy, float len) {
        float gap = dp(6);
        float half = len * 0.40f;
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * gap;
            arrow(canvas, x, cy + 2, x, cy - half);
            arrow(canvas, x, cy - 2, x, cy + half);
        }
    }

    private void drawFace(Canvas canvas, float cx, float cy, float len) {
        float gap = dp(7);
        float x1 = cx - len/2;
        float x2 = cx + len/2;
        for (int i = 0; i < 3; i++) {
            float y = cy + (i - 1) * gap;
            arrow(canvas, x1, y, x2, y);
        }
    }
}
