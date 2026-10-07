package com.senz.calc;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.animation.DecelerateInterpolator;

/**
 * Состояние одной "волны" (ripple): откуда началась, когда нажали и когда отпустили.
 * Сама ничего не рисует, только считает прогресс расширения и прозрачность,
 * поэтому анимация плавная и не зависит от частоты кадров.
 */
final class Ripple {

    static final long EXPAND = 420;     // сколько расширяется круг
    static final long FADE_DELAY = 130; // минимум, сколько волна держится до затухания
    static final long FADE = 360;       // длительность затухания после отпускания

    private static final DecelerateInterpolator DEC = new DecelerateInterpolator(1.5f);

    float x, y;
    long down, up;
    boolean active, held;

    void start(float px, float py) {
        x = px;
        y = py;
        down = SystemClock.uptimeMillis();
        up = 0;
        held = true;
        active = true;
    }

    void release() {
        if (active && held) {
            held = false;
            up = SystemClock.uptimeMillis();
        }
    }

    /** 0..1 - насколько круг уже раскрылся (с замедлением к концу). */
    float expansion(long now) {
        float f = (now - down) / (float) EXPAND;
        if (f < 0) f = 0;
        if (f > 1) f = 1;
        return DEC.getInterpolation(f);
    }

    /** 0..1 - множитель прозрачности: быстро появляется, плавно гаснет. */
    float alpha(long now) {
        float a = (now - down) / 100f;
        if (a < 0) a = 0;
        if (a > 1) a = 1;
        a = a * a * (3f - 2f * a);
        if (!held) {
            long fs = Math.max(up, down + FADE_DELAY);
            float o = (now - fs) / (float) FADE;
            if (o > 0) {
                if (o >= 1) return 0;
                a *= 1f - o * o * (3f - 2f * o);
            }
        }
        return a;
    }

    boolean finished(long now) {
        if (held) return false;
        long fs = Math.max(up, down + FADE_DELAY);
        return now >= fs + FADE;
    }

    /**
     * Рисует волну внутри скруглённого прямоугольника b (радиальный градиент
     * рисуется самой "таблеткой", поэтому за края волна не выходит).
     */
    void draw(Canvas c, Paint p, long now, RectF b, float radius, int color, int maxAlpha) {
        float e = expansion(now);
        float a = alpha(now);
        if (a <= 0f || e <= 0f) return;
        float px = Math.max(b.left, Math.min(b.right, x));
        float py = Math.max(b.top, Math.min(b.bottom, y));
        float cx = px + (b.centerX() - px) * e;
        float cy = py + (b.centerY() - py) * e;
        float full = (float) Math.sqrt(b.width() * b.width() + b.height() * b.height()) / 2f;
        float rad = full * e;
        if (rad < 1f) return;
        int rgb = color & 0x00FFFFFF;
        int on = (((int) (maxAlpha * a)) << 24) | rgb;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, rad, new int[] { on, on, rgb },
                new float[] { 0f, 0.94f, 1f }, Shader.TileMode.CLAMP));
        c.drawRoundRect(b, radius, radius, p);
        p.setShader(null);
    }
}
