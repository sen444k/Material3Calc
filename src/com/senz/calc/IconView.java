package com.senz.calc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Иконка верхней панели (история, три точки, стрелки раскрытия) с круглой
 * ripple-волной. Тип иконки задаётся через android:tag
 * (0 - история, 1 - три точки, 2 - стрелки раскрытия).
 */
public class IconView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ripple ripple = new Ripple();
    private float k = 1f;
    private int type = 0;
    private float progress = 0f;
    private float active = 0f;

    public IconView(Context context) {
        super(context);
        init();
    }

    public IconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IconView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        k = getResources().getDisplayMetrics().widthPixels / 480f;
    }

    /** Для стрелок раскрытия: 0 - свёрнуто, 1 - развёрнуто. */
    public void setProgress(float p) {
        if (p != progress) {
            progress = p;
            invalidate();
        }
    }

    /** Для кнопки истории: 0 - обычная, 1 - с синим кружком (история открыта). */
    public void setActive(float a) {
        if (a != active) {
            active = a;
            invalidate();
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
            ripple.start(getWidth() / 2f, getHeight() / 2f);
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            ripple.release();
            invalidate();
            break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        type = 0;
        try {
            type = Integer.parseInt(String.valueOf(getTag()));
        } catch (Exception ex) {
        }

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        long now = SystemClock.uptimeMillis();

        if (ripple.active) {
            if (ripple.finished(now)) {
                ripple.active = false;
            } else {
                float e = ripple.expansion(now);
                float a = ripple.alpha(now);
                float r = Math.min(getWidth(), getHeight()) / 2f * e;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFC4C7C5);
                paint.setAlpha((int) (46 * a));
                c.drawCircle(cx, cy, r, paint);
                postInvalidateDelayed(8);
            }
        }

        int tint = 0;
        if (active > 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF004A77);
            paint.setAlpha((int) (255 * active));
            c.drawCircle(cx, cy, Math.min(getHeight() / 2f, 25f * k), paint);
            tint = mix(0xFFC4C7C5, 0xFFC2E7FF, active);
        }
        IconPainter.draw(c, paint, type, cx, cy, k, progress, 255, tint);
    }
}
