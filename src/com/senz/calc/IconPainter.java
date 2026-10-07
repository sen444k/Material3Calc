package com.senz.calc;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Рисует иконки кодом. Все координаты сняты со скриншота шириной 480 px,
 * k - во сколько раз масштабировать (ширина экрана / 480, умноженная на масштаб кнопки).
 */
final class IconPainter {

    static final int HISTORY = 0, MORE = 1, EXPAND = 2, BACK = 3, DIV = 4,
            MUL = 5, MINUS = 6, PLUS = 7, EQ = 8;

    private static final Path path = new Path();
    private static final RectF oval = new RectF();

    private IconPainter() {
    }

    private static void stroke(Paint p, float w, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(w);
        p.setColor(color);
    }

    private static void line(Canvas c, Paint p, float cx, float cy, float k,
            float x1, float y1, float x2, float y2) {
        c.drawLine(cx + x1 * k, cy + y1 * k, cx + x2 * k, cy + y2 * k, p);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * @param progress только для EXPAND: 0 - стрелки "раскрыть" (вверх/вниз),
     *                 1 - стрелки "свернуть" (навстречу друг другу)
     * @param alpha    0..255 - общая прозрачность иконки
     */
    static void draw(Canvas c, Paint p, int type, float cx, float cy, float k,
            float progress, int alpha) {
        draw(c, p, type, cx, cy, k, progress, alpha, 0);
    }

    /** tint != 0 перекрашивает иконки верхней панели (история, точки, стрелки). */
    static void draw(Canvas c, Paint p, int type, float cx, float cy, float k,
            float progress, int alpha, int tint) {
        switch (type) {
        case MORE:
            p.setStyle(Paint.Style.FILL);
            p.setColor(tint != 0 ? tint : 0xFFC4C7C5);
            p.setAlpha(alpha);
            c.drawCircle(cx, cy - 7.5f * k, 2.5f * k, p);
            c.drawCircle(cx, cy, 2.5f * k, p);
            c.drawCircle(cx, cy + 7.5f * k, 2.5f * k, p);
            break;

        case EXPAND: {
            stroke(p, 2.4f * k, tint != 0 ? tint : 0xFFC4C7C5);
            p.setAlpha(alpha);
            float apex = lerp(11.3f, 4.4f, progress);
            float arm = lerp(4.4f, 11.3f, progress);
            // верхняя стрелка
            path.reset();
            path.moveTo(cx - 7f * k, cy - arm * k);
            path.lineTo(cx, cy - apex * k);
            path.lineTo(cx + 7f * k, cy - arm * k);
            c.drawPath(path, p);
            // нижняя стрелка
            path.reset();
            path.moveTo(cx - 7f * k, cy + arm * k);
            path.lineTo(cx, cy + apex * k);
            path.lineTo(cx + 7f * k, cy + arm * k);
            c.drawPath(path, p);
            break;
        }

        case BACK:
            stroke(p, 2.5f * k, 0xFFE3E3E3);
            p.setAlpha(alpha);
            path.reset();
            path.moveTo(cx - 15.5f * k, cy);
            path.lineTo(cx - 6.5f * k, cy - 12f * k);
            path.lineTo(cx + 15.5f * k, cy - 12f * k);
            path.lineTo(cx + 15.5f * k, cy + 12f * k);
            path.lineTo(cx - 6.5f * k, cy + 12f * k);
            path.close();
            c.drawPath(path, p);
            line(c, p, cx, cy, k, -2.2f, -5f, 8.8f, 5.5f);
            line(c, p, cx, cy, k, 8.8f, -5f, -2.2f, 5.5f);
            break;

        case DIV:
            stroke(p, 2.6f * k, 0xFFC2E7FF);
            p.setAlpha(alpha);
            line(c, p, cx, cy, k, -10.5f, 1.5f, 10.5f, 1.5f);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy - 8f * k, 2.6f * k, p);
            c.drawCircle(cx, cy + 11f * k, 2.6f * k, p);
            break;

        case MUL:
            stroke(p, 2.6f * k, 0xFFC2E7FF);
            p.setAlpha(alpha);
            line(c, p, cx, cy, k, -9f, -8f, 9f, 11f);
            line(c, p, cx, cy, k, 9f, -8f, -9f, 11f);
            break;

        case MINUS:
            stroke(p, 2.6f * k, 0xFFC2E7FF);
            p.setAlpha(alpha);
            line(c, p, cx, cy, k, -10f, 1.5f, 10f, 1.5f);
            break;

        case PLUS:
            stroke(p, 2.6f * k, 0xFFC2E7FF);
            p.setAlpha(alpha);
            line(c, p, cx, cy, k, -10.5f, 1.5f, 10.5f, 1.5f);
            line(c, p, cx, cy, k, 0, -9f, 0, 12f);
            break;

        case EQ:
            stroke(p, 2.8f * k, 0xFF0A3818);
            p.setAlpha(alpha);
            line(c, p, cx, cy, k, -10.5f, -3.5f, 10.5f, -3.5f);
            line(c, p, cx, cy, k, -10.5f, 7f, 10.5f, 7f);
            break;

        default: // HISTORY
            stroke(p, 2.2f * k, tint != 0 ? tint : 0xFFC4C7C5);
            p.setAlpha(alpha);
            oval.set(cx - 9.5f * k, cy - 10f * k, cx + 10.5f * k, cy + 10f * k);
            c.drawArc(oval, 180f, -225f, false, p);
            path.reset();
            path.moveTo(cx, cy - 7f * k);
            path.lineTo(cx, cy - 0.8f * k);
            path.lineTo(cx + 5.6f * k, cy + 3.6f * k);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(cx - 11f * k, cy - 11f * k);
            path.lineTo(cx - 11f * k, cy - 3f * k);
            path.lineTo(cx - 3f * k, cy - 3f * k);
            path.close();
            c.drawPath(path, p);
            break;
        }
    }
}
