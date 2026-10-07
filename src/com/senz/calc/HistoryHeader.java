package com.senz.calc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Полоска "ручка + подпись Текущее выражение" в верхней части шторки.
 * Появляется по мере того, как шторка уезжает вниз (progress 0..1).
 */
public class HistoryHeader extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float k = 1f;
    private float progress = 0f;
    private String label = "";

    public HistoryHeader(Context context) {
        super(context);
        init();
    }

    public HistoryHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HistoryHeader(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        k = getResources().getDisplayMetrics().widthPixels / 480f;
        label = getResources().getString(R.string.current_expression);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    public void setProgress(float p) {
        if (p != progress) {
            progress = p;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(56f * k));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (progress <= 0f) {
            return;
        }
        int a = (int) (255 * progress);

        // ручка: 31 x 5, верх на 13 от верха шторки
        float cx = getWidth() / 2f;
        rect.set(cx - 15.5f * k, 13f * k, cx + 15.5f * k, 18f * k);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFC4C7C5);
        paint.setAlpha(a);
        c.drawRoundRect(rect, 2.5f * k, 2.5f * k, paint);

        // подпись
        paint.setColor(0xFFE3E3E3);
        paint.setAlpha(a);
        paint.setTextSize(18f * k);
        c.drawText(label, 27f * k, 38f * k, paint);
    }
}
