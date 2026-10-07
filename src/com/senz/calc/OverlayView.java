package com.senz.calc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Слой поверх всего окна: выпадающее меню и диалоги. Всё рисуется кодом, поэтому
 * анимации одинаково плавные на любой версии Android (в том числе 2.2).
 *
 * Меню: вырастает из точки (трёх точек) и проявляется, закрывается обратной анимацией.
 * Диалог: сначала маленький и прозрачный, быстро увеличивается и проявляется,
 * закрывается обратной анимацией. Пункты и кнопки имеют ripple.
 */
public class OverlayView extends View {

    public interface Callback {
        /** Выбран пункт меню. Вызывается в момент, когда меню начинает закрываться. */
        void onMenuItem(int id);

        /** Прогресс меню 0..1 (для подсветки трёх точек). */
        void onMenuProgress(float p);

        /** Диалог закрывается. positive - нажата правая (основная) кнопка. */
        void onDialogResult(int dialogId, boolean positive);
    }

    public static final int ITEM_CLEAR = 1;
    public static final int ITEM_THEME = 2;

    private static final int C_MENU = 0xFF333537;
    private static final int C_DIALOG = 0xFF282A2C;
    private static final int C_TEXT = 0xFFE3E3E3;
    private static final int C_ICON = 0xFFC4C7C5;
    private static final int C_BUTTON = 0xFFA8C7FA;

    private static final long MENU_OPEN = 280;
    private static final long MENU_CLOSE = 200;
    private static final long DLG_OPEN = 190;
    private static final long DLG_CLOSE = 150;
    private static final long SELECT_DELAY = 500; // пауза перед закрытием меню после нажатия
    private static final long BUTTON_DELAY = 160; // пауза перед закрытием диалога

    private static final float ROW_H = 61f;
    private static final float CARD_PAD = 11.5f;

    /** Линейный прогресс 0..1 с возможностью развернуть анимацию в любой момент. */
    private static class Anim {
        float x = 0f;
        boolean running = false;
        float from, to;
        long start, dur;

        void go(float target, long fullDuration) {
            from = x;
            to = target;
            dur = Math.max(1, (long) (fullDuration * Math.abs(to - from)));
            start = SystemClock.uptimeMillis();
            running = true;
        }

        void step(long now) {
            if (!running) return;
            float f = (now - start) / (float) dur;
            if (f >= 1f) {
                f = 1f;
                running = false;
            }
            x = from + (to - from) * f;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    private Callback cb;
    private boolean locked = false;

    // ---- меню
    private final Anim menuAnim = new Anim();
    private boolean menuWanted = false;
    private boolean menuLayoutNeeded = false;
    private float anchorX, anchorY;
    private final RectF menuCard = new RectF();
    private final String[] menuLabels;
    private final int[] menuIds = { ITEM_CLEAR, ITEM_THEME };
    private final Ripple[] menuRipples = { new Ripple(), new Ripple() };
    private int menuPressed = -1;
    private boolean menuDownOutside = false;

    // ---- диалог
    private final Anim dlgAnim = new Anim();
    private boolean dlgWanted = false;
    private boolean dlgLayoutNeeded = false;
    private int dlgId;
    private String dlgTitle, dlgNeg, dlgPos;
    private final RectF dlgCard = new RectF();
    private final RectF[] dlgBtn = { new RectF(), new RectF() }; // 0 - левая, 1 - правая
    private final Ripple[] dlgRipples = { new Ripple(), new Ripple() };
    private int dlgPressed = -1;
    private boolean dlgDownOutside = false;

    // диалог, ожидающий закрытия меню
    private boolean queued = false;
    private int qId;
    private String qTitle, qNeg, qPos;

    public OverlayView(Context context) {
        super(context);
        setVisibility(View.GONE);
        menuLabels = new String[] {
                context.getResources().getString(R.string.clear_history),
                context.getResources().getString(R.string.choose_theme) };
    }

    public void setCallback(Callback c) {
        cb = c;
    }

    public boolean isShowing() {
        return getVisibility() == View.VISIBLE;
    }

    // ------------------------------------------------------------ управление

    /** Показывает меню, вырастающее из точки (ax, ay) в координатах этого слоя. */
    public void showMenu(float ax, float ay) {
        if (menuWanted || dlgWanted || queued) return;
        anchorX = ax;
        anchorY = ay;
        menuLayoutNeeded = true;
        menuWanted = true;
        locked = false;
        menuPressed = -1;
        for (int i = 0; i < menuRipples.length; i++) menuRipples[i].active = false;
        menuAnim.go(1f, MENU_OPEN);
        setVisibility(View.VISIBLE);
        invalidate();
    }

    /**
     * Показывает диалог. Если сейчас закрывается меню, диалог появится сразу
     * после его закрытия. neg может быть null (тогда только одна кнопка).
     */
    public void showDialog(int id, String title, String neg, String pos) {
        if (menuAnim.x > 0f || menuAnim.running || menuWanted) {
            queued = true;
            qId = id;
            qTitle = title;
            qNeg = neg;
            qPos = pos;
            setVisibility(View.VISIBLE);
            invalidate();
            return;
        }
        startDialog(id, title, neg, pos);
    }

    private void startDialog(int id, String title, String neg, String pos) {
        dlgId = id;
        dlgTitle = title;
        dlgNeg = neg;
        dlgPos = pos;
        dlgLayoutNeeded = true;
        dlgWanted = true;
        locked = false;
        dlgPressed = -1;
        for (int i = 0; i < dlgRipples.length; i++) dlgRipples[i].active = false;
        dlgAnim.go(1f, DLG_OPEN);
        setVisibility(View.VISIBLE);
        invalidate();
    }

    private void closeMenu() {
        menuWanted = false;
        menuAnim.go(0f, MENU_CLOSE);
        invalidate();
    }

    private void closeDialog() {
        dlgWanted = false;
        dlgAnim.go(0f, DLG_CLOSE);
        invalidate();
    }

    /** Кнопка "Назад": закрывает то, что открыто. Возвращает true, если что-то закрыли. */
    public boolean dismissTop() {
        if (dlgWanted) {
            closeDialog();
            if (cb != null) cb.onDialogResult(dlgId, false);
            return true;
        }
        if (menuWanted) {
            closeMenu();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ раскладка

    private void layoutMenu(float k) {
        menuLayoutNeeded = false;
        paint.setTextSize(20f * k);
        float maxW = 0;
        for (int i = 0; i < menuLabels.length; i++) {
            maxW = Math.max(maxW, paint.measureText(menuLabels[i]));
        }
        float w = 73f * k + maxW + 28f * k;
        float right = getWidth() - 14f * k;
        float top = anchorY + 27f * k;
        menuCard.set(right - w, top, right,
                top + (2f * CARD_PAD + ROW_H * menuLabels.length) * k);
    }

    private void layoutDialog(float k) {
        dlgLayoutNeeded = false;
        float w = getWidth() * 0.825f;
        float h = 182f * k;
        float left = (getWidth() - w) / 2f;
        float top = (getHeight() - h) / 2f;
        dlgCard.set(left, top, left + w, top + h);

        paint.setTextSize(19f * k);
        float right = dlgCard.right - 27f * k;
        float by = dlgCard.top + 111f * k;
        float bh = 48f * k;
        if (dlgPos != null) {
            float bw = paint.measureText(dlgPos) + 48f * k;
            dlgBtn[1].set(right - bw, by, right, by + bh);
            right -= bw;
        } else {
            dlgBtn[1].setEmpty();
        }
        if (dlgNeg != null) {
            float bw = paint.measureText(dlgNeg) + 48f * k;
            dlgBtn[0].set(right - bw, by, right, by + bh);
        } else {
            dlgBtn[0].setEmpty();
        }
    }

    // -------------------------------------------------------------- рисование

    private static float easeOut(float v) {
        float u = 1f - v;
        return 1f - u * u * u;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        menuAnim.step(now);
        dlgAnim.step(now);
        boolean busy = menuAnim.running || dlgAnim.running;

        if (queued && !menuWanted && menuAnim.x <= 0f && !menuAnim.running) {
            queued = false;
            startDialog(qId, qTitle, qNeg, qPos);
            dlgAnim.step(now);
            busy = true;
        }

        float k = getWidth() / 480f;

        if (menuAnim.x > 0f) {
            if (menuLayoutNeeded) layoutMenu(k);
            busy |= drawMenu(c, now, k);
        }
        if (dlgAnim.x > 0f) {
            if (dlgLayoutNeeded) layoutDialog(k);
            busy |= drawDialog(c, now, k);
        }
        if (cb != null) {
            cb.onMenuProgress(easeOut(menuAnim.x));
        }

        if (busy || queued) {
            postInvalidateDelayed(8);
        } else if (menuAnim.x <= 0f && dlgAnim.x <= 0f && !menuWanted && !dlgWanted) {
            post(new Runnable() {
                public void run() {
                    if (!menuWanted && !dlgWanted && !queued && menuAnim.x <= 0f
                            && dlgAnim.x <= 0f && !menuAnim.running && !dlgAnim.running) {
                        setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private void rowRect(int i, float k, RectF out) {
        float top = menuCard.top + (CARD_PAD + ROW_H * i) * k;
        out.set(menuCard.left, top, menuCard.right, top + ROW_H * k);
    }

    private boolean drawMenu(Canvas c, long now, float k) {
        boolean busy = false;
        float e = easeOut(menuAnim.x);
        int alpha = (int) (255 * Math.min(1f, e * 1.6f));

        c.save();
        c.scale(e, e, anchorX, anchorY); // растёт из трёх точек

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(C_MENU);
        paint.setAlpha(alpha);
        c.drawRoundRect(menuCard, 28f * k, 28f * k, paint);

        for (int i = 0; i < menuLabels.length; i++) {
            rowRect(i, k, tmp);
            float cy = tmp.centerY();

            Ripple rp = menuRipples[i];
            if (rp.active) {
                if (rp.finished(now)) {
                    rp.active = false;
                } else {
                    busy = true;
                    tmp.inset(8f * k, 0);
                    rp.draw(c, ripplePaint, now, tmp, tmp.height() / 2f, C_TEXT, 46);
                    tmp.inset(-8f * k, 0);
                }
            }

            drawMenuIcon(c, i, menuCard.left + 36f * k, cy, k, alpha);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(20f * k);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(C_TEXT);
            paint.setAlpha(alpha);
            c.drawText(menuLabels[i], menuCard.left + 73f * k, cy + 0.36f * 20f * k, paint);
        }
        c.restore();
        return busy;
    }

    /** 0 - корзина (очистить историю), 1 - палитра (выбрать тему). */
    private void drawMenuIcon(Canvas c, int type, float cx, float cy, float k, int alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(C_ICON);
        paint.setAlpha(alpha);
        if (type == 0) {
            // крышка и ручка
            c.drawRect(cx - 9f * k, cy - 8f * k, cx + 9f * k, cy - 5.5f * k, paint);
            c.drawRect(cx - 3.5f * k, cy - 11f * k, cx + 3.5f * k, cy - 8f * k, paint);
            // корпус
            tmp.set(cx - 7f * k, cy - 3.5f * k, cx + 7f * k, cy + 10f * k);
            c.drawRoundRect(tmp, 2f * k, 2f * k, paint);
            // прорези
            paint.setColor(C_MENU);
            paint.setAlpha(alpha);
            c.drawRect(cx - 3.2f * k, cy - 0.5f * k, cx - 1.6f * k, cy + 7f * k, paint);
            c.drawRect(cx + 1.6f * k, cy - 0.5f * k, cx + 3.2f * k, cy + 7f * k, paint);
        } else {
            c.drawCircle(cx, cy, 11.5f * k, paint);
            paint.setColor(C_MENU);
            paint.setAlpha(alpha);
            c.drawCircle(cx - 4.5f * k, cy - 3.5f * k, 2f * k, paint);
            c.drawCircle(cx + 1f * k, cy - 6f * k, 2f * k, paint);
            c.drawCircle(cx + 6.5f * k, cy - 2.5f * k, 2f * k, paint);
            c.drawCircle(cx - 6.5f * k, cy + 2.5f * k, 2f * k, paint);
            c.drawCircle(cx + 5.5f * k, cy + 7f * k, 3.5f * k, paint);
        }
    }

    private boolean drawDialog(Canvas c, long now, float k) {
        boolean busy = false;
        float d = dlgAnim.x;
        float e = easeOut(d);
        float scale = 0.8f + 0.2f * e;
        int alpha = (int) (255 * Math.min(1f, d * 2f));

        // затемнение окна
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setAlpha((int) (80 * d));
        c.drawRect(0, 0, getWidth(), getHeight(), paint);

        c.save();
        c.scale(scale, scale, dlgCard.centerX(), dlgCard.centerY());

        paint.setColor(C_DIALOG);
        paint.setAlpha(alpha);
        c.drawRoundRect(dlgCard, 28f * k, 28f * k, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(C_TEXT);
        paint.setAlpha(alpha);
        paint.setTextSize(22f * k);
        c.drawText(dlgTitle, dlgCard.left + 25f * k, dlgCard.top + 50f * k, paint);

        for (int i = 0; i < 2; i++) {
            String label = (i == 0) ? dlgNeg : dlgPos;
            if (label == null) continue;
            RectF r = dlgBtn[i];

            Ripple rp = dlgRipples[i];
            if (rp.active) {
                if (rp.finished(now)) {
                    rp.active = false;
                } else {
                    busy = true;
                    rp.draw(c, ripplePaint, now, r, r.height() / 2f, C_BUTTON, 52);
                }
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(19f * k);
            paint.setColor(C_BUTTON);
            paint.setAlpha(alpha);
            float w = paint.measureText(label);
            c.drawText(label, r.centerX() - w / 2f, r.centerY() + 0.36f * 19f * k, paint);
        }
        c.restore();
        return busy;
    }

    // ---------------------------------------------------------------- касания

    private int menuHit(float x, float y, float k) {
        for (int i = 0; i < menuLabels.length; i++) {
            rowRect(i, k, tmp);
            if (tmp.contains(x, y)) return i;
        }
        return -1;
    }

    private int dlgHit(float x, float y) {
        for (int i = 0; i < 2; i++) {
            if (!dlgBtn[i].isEmpty() && dlgBtn[i].contains(x, y)) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (dlgWanted) {
            touchDialog(e);
        } else if (menuWanted) {
            touchMenu(e);
        }
        return true; // пока слой виден, всё остальное под ним не реагирует
    }

    private void touchMenu(MotionEvent e) {
        if (locked || menuAnim.x < 0.95f) return;
        float k = getWidth() / 480f;
        float x = e.getX();
        float y = e.getY();
        switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
            menuPressed = menuHit(x, y, k);
            menuDownOutside = !menuCard.contains(x, y);
            if (menuPressed >= 0) {
                menuRipples[menuPressed].start(x, y);
                invalidate();
            }
            break;
        case MotionEvent.ACTION_MOVE:
            if (menuPressed >= 0 && menuHit(x, y, k) != menuPressed) {
                menuRipples[menuPressed].release();
                menuPressed = -1;
                invalidate();
            }
            break;
        case MotionEvent.ACTION_UP:
            if (menuPressed >= 0) {
                menuRipples[menuPressed].release();
                locked = true;
                final int id = menuIds[menuPressed];
                menuPressed = -1;
                postDelayed(new Runnable() {
                    public void run() {
                        closeMenu();
                        if (cb != null) cb.onMenuItem(id);
                    }
                }, SELECT_DELAY);
                invalidate();
            } else if (menuDownOutside && !menuCard.contains(x, y)) {
                closeMenu();
            }
            menuPressed = -1;
            break;
        case MotionEvent.ACTION_CANCEL:
            if (menuPressed >= 0) {
                menuRipples[menuPressed].release();
            }
            menuPressed = -1;
            break;
        }
    }

    private void touchDialog(MotionEvent e) {
        if (locked || dlgAnim.x < 0.95f) return;
        float x = e.getX();
        float y = e.getY();
        switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
            dlgPressed = dlgHit(x, y);
            dlgDownOutside = !dlgCard.contains(x, y);
            if (dlgPressed >= 0) {
                dlgRipples[dlgPressed].start(x, y);
                invalidate();
            }
            break;
        case MotionEvent.ACTION_MOVE:
            if (dlgPressed >= 0 && dlgHit(x, y) != dlgPressed) {
                dlgRipples[dlgPressed].release();
                dlgPressed = -1;
                invalidate();
            }
            break;
        case MotionEvent.ACTION_UP:
            if (dlgPressed >= 0) {
                dlgRipples[dlgPressed].release();
                locked = true;
                final boolean positive = (dlgPressed == 1);
                dlgPressed = -1;
                postDelayed(new Runnable() {
                    public void run() {
                        closeDialog();
                        if (cb != null) cb.onDialogResult(dlgId, positive);
                    }
                }, BUTTON_DELAY);
                invalidate();
            } else if (dlgDownOutside && !dlgCard.contains(x, y)) {
                locked = true;
                closeDialog();
                if (cb != null) cb.onDialogResult(dlgId, false);
            }
            dlgPressed = -1;
            break;
        case MotionEvent.ACTION_CANCEL:
            if (dlgPressed >= 0) {
                dlgRipples[dlgPressed].release();
            }
            dlgPressed = -1;
            break;
        }
    }
}
