package com.senz.calc;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Контейнер из двух слоёв: снизу список истории, сверху "шторка" (дисплей и клавиатура).
 * Шторка - последний дочерний элемент. При открытии истории она плавно уезжает вниз
 * и останавливается так, что остаётся видна только её верхняя полоска
 * (ручка, подпись и текущее выражение).
 *
 * Шторка двигается через offsetTopAndBottom, поэтому касания и перерисовка
 * работают правильно даже на Android 2.2 (там нет translationY).
 */
public class SheetHost extends FrameLayout {

    public interface Listener {
        /** p: 0 - обычный вид, 1 - история открыта (со сглаживанием). */
        void onProgress(float p);

        /** Пользователь смахнул шторку вверх. */
        void onSwipeUp();
    }

    private static final long DURATION = 450;
    /** Какая доля высоты остаётся видна у шторки внизу (186 из 698). */
    private static final float STRIP = 186f / 698f;

    private Listener listener;
    private boolean open = false;
    private boolean animating = false;
    private float x = 0f; // линейный прогресс
    private float from, to;
    private long start, dur;
    private int curOffset = 0;

    private final int slop;
    private float downX, downY;
    private boolean swiping = false;

    private final Runnable step = new Runnable() {
        public void run() {
            float f = (SystemClock.uptimeMillis() - start) / (float) dur;
            if (f >= 1f) {
                f = 1f;
                animating = false;
            }
            x = from + (to - from) * f;
            applyOffset();
            if (listener != null) {
                listener.onProgress(eased());
            }
            if (animating) {
                postDelayed(this, 10);
            }
        }
    };

    public SheetHost(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SheetHost(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SheetHost(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isOpen() {
        return open;
    }

    /** Высота видимой полоски шторки в пикселях (для отступа списка истории). */
    public int stripHeight() {
        return Math.round(getHeight() * STRIP);
    }

    public void setOpen(boolean value) {
        if (value == open) {
            return;
        }
        open = value;
        from = x;
        to = value ? 1f : 0f;
        dur = Math.max(1, (long) (DURATION * Math.abs(to - from)));
        start = SystemClock.uptimeMillis();
        animating = true;
        removeCallbacks(step);
        post(step);
    }

    private float eased() {
        return (float) (1.0 - Math.cos(x * Math.PI)) / 2f;
    }

    private View sheet() {
        return getChildAt(getChildCount() - 1);
    }

    private void applyOffset() {
        View s = sheet();
        if (s == null) {
            return;
        }
        int want = Math.round(eased() * getHeight() * (1f - STRIP));
        if (want != curOffset) {
            s.offsetTopAndBottom(want - curOffset);
            curOffset = want;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        curOffset = 0; // после раскладки шторка снова на базовом месте
        applyOffset();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!open || x < 0.9f) {
            return false;
        }
        switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
            downX = e.getX();
            downY = e.getY();
            swiping = false;
            break;
        case MotionEvent.ACTION_MOVE:
            float dy = downY - e.getY();
            float dx = Math.abs(e.getX() - downX);
            View s = sheet();
            if (s != null && downY >= s.getTop() && dy > slop * 1.5f && dy > dx) {
                swiping = true;
                return true;
            }
            break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (swiping) {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                swiping = false;
                if (a == MotionEvent.ACTION_UP && listener != null) {
                    listener.onSwipeUp();
                }
            }
            return true;
        }
        return super.onTouchEvent(e);
    }
}
