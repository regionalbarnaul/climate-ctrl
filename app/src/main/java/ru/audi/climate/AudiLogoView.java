package ru.audi.climate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class AudiLogoView extends View {

    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTextShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTextHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint paintRingBody = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRingHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRingDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRingShadow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final String text = "AUDI";

    public AudiLogoView(Context c) { this(c, null); }

    public AudiLogoView(Context c, AttributeSet a) {
        super(c, a);

        float textSize = sp(11f);

        paintText.setColor(0xFFFFFFFF);
        paintText.setTextSize(textSize);
        paintText.setFakeBoldText(true);
        paintText.setLetterSpacing(0.35f);
        paintText.setTextAlign(Paint.Align.CENTER);

        paintTextShadow.setColor(0xAA000000);
        paintTextShadow.setTextSize(textSize);
        paintTextShadow.setFakeBoldText(true);
        paintTextShadow.setLetterSpacing(0.35f);
        paintTextShadow.setTextAlign(Paint.Align.CENTER);

        paintTextHighlight.setColor(0x88FFFFFF);
        paintTextHighlight.setTextSize(textSize);
        paintTextHighlight.setFakeBoldText(true);
        paintTextHighlight.setLetterSpacing(0.35f);
        paintTextHighlight.setTextAlign(Paint.Align.CENTER);

        paintRingBody.setStyle(Paint.Style.STROKE);
        paintRingBody.setStrokeWidth(dp(3.5f));
        paintRingBody.setStrokeCap(Paint.Cap.ROUND);

        paintRingHighlight.setStyle(Paint.Style.STROKE);
        paintRingHighlight.setStrokeWidth(dp(1f));
        paintRingHighlight.setColor(0xCCFFFFFF);
        paintRingHighlight.setStrokeCap(Paint.Cap.ROUND);

        paintRingDark.setStyle(Paint.Style.STROKE);
        paintRingDark.setStrokeWidth(dp(1f));
        paintRingDark.setColor(0x88000000);
        paintRingDark.setStrokeCap(Paint.Cap.ROUND);

        paintRingShadow.setStyle(Paint.Style.STROKE);
        paintRingShadow.setStrokeWidth(dp(3.5f));
        paintRingShadow.setColor(0x66000000);
        paintRingShadow.setStrokeCap(Paint.Cap.ROUND);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // Хром: белое → серое → тёмное → снова светлое
        LinearGradient g = new LinearGradient(
            0, 0, 0, h,
            new int[]{ 0xFFFFFFFF, 0xFFDDDDDD, 0xFF888888, 0xFF555555, 0xFFBBBBBB },
            new float[]{ 0f, 0.35f, 0.55f, 0.75f, 1f },
            Shader.TileMode.CLAMP);
        paintRingBody.setShader(g);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();

        // Текст — верхние 35%, кольца — остальное
        float textArea = h * 0.35f;
        float ringsArea = h - textArea;

        // ===== Текст металлик =====
        float tx = w / 2f;
        float ty = textArea / 2f - (paintText.ascent() + paintText.descent()) / 2f;
        canvas.drawText(text, tx, ty + dp(1f), paintTextShadow);   // тень снизу
        canvas.drawText(text, tx, ty, paintText);                   // тело (белое)
        canvas.drawText(text, tx, ty - dp(0.6f), paintTextHighlight); // блик сверху

        // ===== Кольца металлик =====
        float cy = textArea + ringsArea / 2f;
        float r = ringsArea * 0.40f;
        float gap = r * 1.15f;
        float total = r * 2 + gap * 3;
        float startX = (w - total) / 2f + r;

        RectF oval = new RectF();

        // 1) Тень снизу
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(startX + i * gap, cy + dp(1f), r, paintRingShadow);
        }
        // 2) Тело с хром-градиентом
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(startX + i * gap, cy, r, paintRingBody);
        }
        // 3) Тёмный низ
        for (int i = 0; i < 4; i++) {
            float cx = startX + i * gap;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(oval, 20f, 140f, false, paintRingDark);
        }
        // 4) Светлый блик сверху
        for (int i = 0; i < 4; i++) {
            float cx = startX + i * gap;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(oval, 200f, 140f, false, paintRingHighlight);
        }
    }
}
