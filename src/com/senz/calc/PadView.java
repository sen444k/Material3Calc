package com.senz.calc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Вся клавиатура калькулятора в одном View: 8 рядов по 4 кнопки.
 * Нижние 5 рядов - обычные кнопки, верхние 3 - инженерные.
 *
 * Свёрнуто: инженерные ряды имеют высоту 0, обычные занимают всю высоту.
 * Развёрнуто: все 8 рядов делят высоту поровну. Между ними плавная анимация,
 * при которой обычные кнопки сжимаются, а инженерные выезжают сверху.
 *
 * Размеры в "дизайн-пикселях": ширина 480, высота 435 (как на скриншоте),
 * на реальном экране масштабируются по ширине и высоте View.
 */
public class PadView extends View {

    public interface PadListener {
        void onKey(int key);

        void onLongKey(int key);

        /** t: 0 - свёрнуто, 1 - развёрнуто (с учётом сглаживания). */
        void onExpandProgress(float t);
    }

    // ---- коды кнопок (цифры 0..9 имеют коды 0..9)
    public static final int K_COMMA = 10, K_AC = 11, K_BRACKET = 12,
            K_PERCENT = 13, K_DIV = 14, K_MUL = 15, K_MINUS = 16, K_PLUS = 17,
            K_BACK = 18, K_EQ = 19, K_SQRT = 20, K_PI = 21, K_POW = 22,
            K_FACT = 23, K_DEG = 24, K_SIN = 25, K_COS = 26, K_TAN = 27,
            K_INV = 28, K_E = 29, K_LN = 30, K_LOG = 31;

    private static final int[][] ROWS = {
            { K_SQRT, K_PI, K_POW, K_FACT },
            { K_DEG, K_SIN, K_COS, K_TAN },
            { K_INV, K_E, K_LN, K_LOG },
            { K_AC, K_BRACKET, K_PERCENT, K_DIV },
            { 7, 8, 9, K_MUL },
            { 4, 5, 6, K_MINUS },
            { 1, 2, 3, K_PLUS },
            { 0, K_COMMA, K_BACK, K_EQ } };

    private static final int ENG_ROWS = 3;

    // ---- геометрия (дизайн-пиксели)
    private static final float[] COL_L = { 5, 124, 243, 362 };
    private static final float COL_W = 113;
    private static final float[] MAIN_TOP = { 0, 88, 176, 265, 353 };
    private static final float[] MAIN_H = { 82, 82, 83, 82, 82 };
    private static final float EXP_H = 49.125f;
    private static final float EXP_PITCH = 55.125f;
    private static final float DESIGN_W = 480f;
    private static final float DESIGN_H = 435f;
    private static final float BASE_TEXT = 38f; // размер текста обычной кнопки
    private static final float BASE_ROW_H = 82f;

    private static final long DURATION = 380;
    private static final long LONG_PRESS = 500;

    // ---- цвета
    private static final int BG_DIGIT = 0xFF333537;
    private static final int BG_OP = 0xFF004A77;
    private static final int BG_AC = 0xFF0842A0;
    private static final int BG_EQ = 0xFF6DD58C;
    private static final int TX_DIGIT = 0xFFE3E3E3;
    private static final int TX_OP = 0xFFC2E7FF;
    private static final int TX_AC = 0xFFD3E3FD;
    private static final int TX_EQ = 0xFF0A3818;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] geom = new float[2];
    private final Ripple[] ripples = new Ripple[32];

    private PadListener listener;
    private boolean inverse = false;
    private boolean degrees = true;

    // анимация раскрытия
    private float x = 0f; // линейный прогресс 0..1
    private boolean expanded = false;
    private boolean animating = false;
    private float animFrom, animTo;
    private long animStart, animDur;
    private float lastReported = -1f;

    // касание
    private int pressedKey = -1;
    private boolean longFired = false;
    private final Runnable longPress = new Runnable() {
        public void run() {
            if (pressedKey == K_BACK && listener != null) {
                longFired = true;
                listener.onLongKey(pressedKey);
            }
        }
    };

    public PadView(Context context) {
        super(context);
        init();
    }

    public PadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PadView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        for (int i = 0; i < ripples.length; i++) {
            ripples[i] = new Ripple();
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    // ------------------------------------------------------------ публичное

    public void setPadListener(PadListener l) {
        listener = l;
    }

    public void setInverse(boolean v) {
        inverse = v;
        invalidate();
    }

    public void setDegrees(boolean v) {
        degrees = v;
        invalidate();
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Плавно раскрывает или сворачивает инженерную панель. */
    public void toggle() {
        long now = SystemClock.uptimeMillis();
        float cur = computeX(now);
        expanded = !expanded;
        animFrom = cur;
        animTo = expanded ? 1f : 0f;
        animDur = Math.max(1, (long) (DURATION * Math.abs(animTo - animFrom)));
        animStart = now;
        animating = true;
        invalidate();
    }

    // ------------------------------------------------------------- анимация

    private float computeX(long now) {
        if (!animating) {
            return x;
        }
        float f = (now - animStart) / (float) animDur;
        if (f > 1f) f = 1f;
        if (f < 0f) f = 0f;
        return animFrom + (animTo - animFrom) * f;
    }

    private static float ease(float v) {
        return (float) (1.0 - Math.cos(v * Math.PI)) / 2f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Верх и высота ряда в дизайн-пикселях для прогресса t. */
    private void rowGeom(int r, float t) {
        if (r < ENG_ROWS) {
            geom[0] = r * EXP_PITCH * t;
            geom[1] = EXP_H * t;
        } else {
            int m = r - ENG_ROWS;
            geom[0] = lerp(MAIN_TOP[m], r * EXP_PITCH, t);
            geom[1] = lerp(MAIN_H[m], EXP_H, t);
        }
    }

    // --------------------------------------------------------------- подписи

    private static int styleBg(int key) {
        if (key == K_AC) return BG_AC;
        if (key == K_EQ) return BG_EQ;
        if (key <= 9 || key == K_COMMA || key == K_BACK) return BG_DIGIT;
        return BG_OP;
    }

    private static int styleText(int key) {
        if (key == K_AC) return TX_AC;
        if (key == K_EQ) return TX_EQ;
        if (key <= 9 || key == K_COMMA || key == K_BACK) return TX_DIGIT;
        return TX_OP;
    }

    private static int iconType(int key) {
        switch (key) {
        case K_DIV: return IconPainter.DIV;
        case K_MUL: return IconPainter.MUL;
        case K_MINUS: return IconPainter.MINUS;
        case K_PLUS: return IconPainter.PLUS;
        case K_BACK: return IconPainter.BACK;
        case K_EQ: return IconPainter.EQ;
        }
        return -1;
    }

    /** '~' отделяет верхний индекс: "x~2" - x в квадрате. */
    private String label(int key) {
        if (key <= 9) return String.valueOf((char) ('0' + key));
        switch (key) {
        case K_COMMA: return ",";
        case K_AC: return "AC";
        case K_BRACKET: return "( )";
        case K_PERCENT: return "%";
        case K_SQRT: return inverse ? "x~2" : "\u221A";
        case K_PI: return "\u03C0";
        case K_POW: return "^";
        case K_FACT: return "!";
        case K_DEG: return degrees ? "Deg" : "Rad";
        case K_SIN: return inverse ? "sin~\u22121" : "sin";
        case K_COS: return inverse ? "cos~\u22121" : "cos";
        case K_TAN: return inverse ? "tan~\u22121" : "tan";
        case K_INV: return "Inv";
        case K_E: return "e";
        case K_LN: return inverse ? "e~x" : "ln";
        case K_LOG: return inverse ? "10~x" : "log";
        }
        return "";
    }

    // -------------------------------------------------------------- рисование

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean busy = false;

        x = computeX(now);
        if (animating && now - animStart >= animDur) {
            animating = false;
            x = animTo;
        }
        float t = ease(x);
        if (animating) busy = true;

        if (listener != null && t != lastReported) {
            lastReported = t;
            listener.onExpandProgress(t);
        }

        float sx = getWidth() / DESIGN_W;
        float sy = getHeight() / DESIGN_H;
        float k = sx;

        for (int r = 0; r < ROWS.length; r++) {
            rowGeom(r, t);
            float topD = geom[0];
            float hD = geom[1];
            if (hD < 1.5f) {
                continue;
            }
            float cs = hD / BASE_ROW_H; // во сколько раз содержимое меньше обычного
            float contentAlpha = (r < ENG_ROWS) ? t : 1f;

            for (int col = 0; col < 4; col++) {
                int key = ROWS[r][col];
                float l = COL_L[col] * sx;
                float rr = (COL_L[col] + COL_W) * sx;
                float tp = topD * sy;
                float bt = (topD + hD) * sy;
                rect.set(l, tp, rr, bt);
                float radius = Math.min(rect.width(), rect.height()) / 2f;

                // фон
                boolean invActive = (key == K_INV && inverse);
                bgPaint.setStyle(Paint.Style.FILL);
                bgPaint.setColor(invActive ? TX_OP : styleBg(key));
                c.drawRoundRect(rect, radius, radius, bgPaint);

                int fg = invActive ? BG_OP : styleText(key);

                // ripple
                Ripple rp = ripples[key];
                if (rp.active) {
                    if (rp.finished(now)) {
                        rp.active = false;
                    } else {
                        busy = true;
                        drawRipple(c, rp, now, rect, radius, fg);
                    }
                }

                // содержимое
                float cx = (l + rr) / 2f;
                float cy = (tp + bt) / 2f;
                int alpha = (int) (255 * contentAlpha);
                if (alpha > 255) alpha = 255;
                int icon = iconType(key);
                if (icon >= 0) {
                    IconPainter.draw(c, iconPaint, icon, cx, cy, k * cs, 0f, alpha);
                } else {
                    drawLabel(c, label(key), cx, cy, BASE_TEXT * k * cs, fg, alpha);
                }
            }
        }

        if (busy) {
            postInvalidateDelayed(8);
        }
    }

    /**
     * Волна рисуется той же "таблеткой", что и кнопка, но с радиальным градиентом
     * вместо сплошной заливки. Поэтому она никогда не выходит за скруглённые края,
     * и для этого не нужны ни слои, ни маски (работает на любой версии Android).
     */
    private void drawRipple(Canvas c, Ripple rp, long now, RectF b, float radius, int color) {
        float e = rp.expansion(now);
        float a = rp.alpha(now);
        if (a <= 0f || e <= 0f) return;

        float px = Math.max(b.left, Math.min(b.right, rp.x));
        float py = Math.max(b.top, Math.min(b.bottom, rp.y));
        float ccx = lerp(px, b.centerX(), e);
        float ccy = lerp(py, b.centerY(), e);
        float full = (float) Math.sqrt(b.width() * b.width() + b.height() * b.height()) / 2f;
        float rad = full * e;
        if (rad < 1f) return;

        int rgb = color & 0x00FFFFFF;
        int on = (((int) (64 * a)) << 24) | rgb;
        ripplePaint.setStyle(Paint.Style.FILL);
        ripplePaint.setShader(new RadialGradient(ccx, ccy, rad,
                new int[] { on, on, rgb }, new float[] { 0f, 0.94f, 1f },
                Shader.TileMode.CLAMP));
        c.drawRoundRect(b, radius, radius, ripplePaint);
        ripplePaint.setShader(null);
    }

    private void drawLabel(Canvas c, String text, float cx, float cy, float size,
            int color, int alpha) {
        textPaint.setColor(color);
        textPaint.setAlpha(alpha);
        int sup = text.indexOf('~');
        float baseline = cy + 0.36f * size; // центр по высоте заглавных букв
        if (sup < 0) {
            textPaint.setTextSize(size);
            float w = textPaint.measureText(text);
            c.drawText(text, cx - w / 2f, baseline, textPaint);
        } else {
            String base = text.substring(0, sup);
            String top = text.substring(sup + 1);
            textPaint.setTextSize(size);
            float wb = textPaint.measureText(base);
            textPaint.setTextSize(size * 0.62f);
            float wt = textPaint.measureText(top);
            float x0 = cx - (wb + wt) / 2f;
            textPaint.setTextSize(size);
            c.drawText(base, x0, baseline, textPaint);
            textPaint.setTextSize(size * 0.62f);
            c.drawText(top, x0 + wb, baseline - size * 0.36f, textPaint);
        }
    }

    // ---------------------------------------------------------------- касания

    /** Возвращает позицию кнопки (ряд*4+колонка) под пальцем или -1. */
    private int hit(float px, float py) {
        float sx = getWidth() / DESIGN_W;
        float sy = getHeight() / DESIGN_H;
        float t = ease(computeX(SystemClock.uptimeMillis()));
        float slop = 3f * sx;
        for (int r = 0; r < ROWS.length; r++) {
            rowGeom(r, t);
            if (geom[1] < 12f) continue;
            float tp = geom[0] * sy - slop;
            float bt = (geom[0] + geom[1]) * sy + slop;
            if (py < tp || py > bt) continue;
            for (int col = 0; col < 4; col++) {
                float l = COL_L[col] * sx - slop;
                float rr = (COL_L[col] + COL_W) * sx + slop;
                if (px >= l && px <= rr) {
                    return r * 4 + col;
                }
            }
        }
        return -1;
    }

    private boolean stillOn(int key, float px, float py) {
        int pos = hit(px, py);
        return pos >= 0 && ROWS[pos / 4][pos % 4] == key;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float px = e.getX();
        float py = e.getY();
        switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
            int pos = hit(px, py);
            if (pos < 0) {
                return false;
            }
            pressedKey = ROWS[pos / 4][pos % 4];
            longFired = false;
            ripples[pressedKey].start(px, py);
            if (pressedKey == K_BACK) {
                postDelayed(longPress, LONG_PRESS);
            }
            invalidate();
            return true;
        }
        case MotionEvent.ACTION_MOVE:
            if (pressedKey >= 0 && !stillOn(pressedKey, px, py)) {
                removeCallbacks(longPress);
                ripples[pressedKey].release();
                pressedKey = -1;
                invalidate();
            }
            return true;
        case MotionEvent.ACTION_UP:
            removeCallbacks(longPress);
            if (pressedKey >= 0) {
                int key = pressedKey;
                pressedKey = -1;
                ripples[key].release();
                if (!longFired && listener != null) {
                    listener.onKey(key);
                }
                invalidate();
            }
            return true;
        case MotionEvent.ACTION_CANCEL:
            removeCallbacks(longPress);
            if (pressedKey >= 0) {
                ripples[pressedKey].release();
                pressedKey = -1;
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(e);
    }
}
