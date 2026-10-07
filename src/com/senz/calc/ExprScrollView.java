package com.senz.calc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;

/**
 * Горизонтально прокручиваемая строка. Текст любой длины не обрезается:
 * его можно двигать пальцем влево-вправо. После смены текста
 * прокрутка автоматически уходит к концу (к последним введённым символам).
 */
public class ExprScrollView extends HorizontalScrollView {

    private boolean stickToEnd = false;

    public ExprScrollView(Context context) {
        super(context);
        init();
    }

    public ExprScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExprScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true); // короткий текст прижимается вправо
    }

    /** Прокрутить к концу сразу после ближайшей раскладки. */
    public void scrollToEndOnLayout() {
        stickToEnd = true;
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (stickToEnd) {
            stickToEnd = false;
            View child = getChildAt(0);
            if (child != null) {
                int max = child.getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight());
                scrollTo(Math.max(0, max), 0);
            }
        }
    }
}
