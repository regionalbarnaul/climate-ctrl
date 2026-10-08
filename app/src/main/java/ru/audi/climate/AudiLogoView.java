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

    private final Paint paintRingDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRingBody = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRingTopDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRingBottomShine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRingShadow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final String text = "AUDI";

    public AudiLogoView(Context c) { this(c, null); }

    public AudiLogoView(Context c, AttributeSet a) {
        super(c, a);

        float textSize = sp(10.5f);

        paintText.setColor(0xFFF0F0F0);
        paintText.setTextSize(textSize);
        paintText.setFakeBoldText(true);
        paintText.setLetterSpacing(0.4f);
        paintText.setTextAlign(Paint.Align.CENTER);

        paintTextShadow.setColor(0xAA000000);
        paintTextShadow.setTextSize(textSize);
        paintTextShadow.setFakeBoldText(true);
        paintTextShadow.setLetterSpacing(0.4f);
        paintTextShadow.setTextAlign(Paint.Align.CENTER);

        paintRingDark.setStyle(Paint.Style.STROKE);
        paintRingDark.setStrokeWidth(dp(6.5f));
        paintRingDark.setColor(0xFF1A1A1A);
        paintRingDark.setStrokeCap(Paint.Cap.ROUND);

        paintRingBody.setStyle(Paint.Style.STROKE);
        paintRingBody.setStrokeWidth(dp(5f));
        paintRingBody.setStrokeCap(Paint.Cap.ROUND);

        paintRingTopDark.setStyle(Paint.Style.STROKE);
        paintRingTopDark.setStrokeWidth(dp(1.2f));
        paintRingTopDark.setColor(0xCC000000);
        paintRingTopDark.setStrokeCap(Paint.Cap.ROUND);

        paintRingBottomShine.setStyle(Paint.Style.STROKE);
        paintRingBottomShine.setStrokeWidth(dp(1.8f));
        paintRingBottomShine.setColor(0xFFFFFFFF);
        paintRingBottomShine.setStrokeCap(Paint.Cap.ROUND);

        paintRingShadow.setStyle(Paint.Style.STROKE);
        paintRingShadow.setStrokeWidth(dp(6f));
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
        LinearGradient g = new LinearGradient(
            0, 0, 0, h,
            new int[]{
                0xFFFFFFFF,
                0xFFE8E8E8,
                0xFF888888,
                0xFF444444,
                0xFF888888,
                0xFFFFFFFF,
                0xFFDDDDDD
            },
            new float[]{ 0f, 0.15f, 0.40f, 0.55f, 0.70f, 0.88f, 1f },
            Shader.TileMode.CLAMP);
        paintRingBody.setShader(g);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();

        float textArea = h * 0.35f;
        float ringsArea = h - textArea;

        // Текст
        float tx = w / 2f;
        float ty = textArea / 2f - (paintText.ascent() + paintText.descent()) / 2f;
        canvas.drawText(text, tx, ty + dp(1f), paintTextShadow);
        canvas.drawText(text, tx, ty, paintText);

        // Кольца — радиус уменьшен, чтобы при gap=1.5r всё влезло по ширине
        float cy = textArea + ringsArea / 2f;
        float r = ringsArea * 0.34f;
        float gap = r * 1.5f;
        float total = r * 2 + gap * 3;
        float startX = (w - total) / 2f + r;

        RectF oval = new RectF();

        // Тень
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(startX + i * gap, cy + dp(1.5f), r, paintRingShadow);
        }
        // Тёмная обводка
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(startX + i * gap, cy, r, paintRingDark);
        }
        // Металлик
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(startX + i * gap, cy, r, paintRingBody);
        }
        // Верхняя тёмная кайма
        for (int i = 0; i < 4; i++) {
            float cx = startX + i * gap;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(oval, 225f, 90f, false, paintRingTopDark);
        }
        // Нижний блик
        for (int i = 0; i < 4; i++) {
            float cx = startX + i * gap;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(oval, 55f, 70f, false, paintRingBottomShine);
        }
    }
}
