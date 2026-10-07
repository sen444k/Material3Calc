package com.senz.calc;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int DLG_CLEAR = 1;
    private static final int DLG_THEME = 2;
    private static final int BG_NORMAL = 0xFF131314;
    private static final int BG_HISTORY = 0xFF1E1F20;

    private final CalcEngine engine = new CalcEngine();

    private View root;
    private SheetHost host;
    private ListView historyList;
    private HistoryHeader header;
    private ExprScrollView svExpression, svResult;
    private TextView tvExpression, tvResult;
    private PadView pad;
    private OverlayView overlay;
    private IconView iconHistory, iconMore, iconExpand;

    private float k; // масштаб относительно скриншота шириной 480 px
    private float ascExpr, ascRes;
    private float historyProgress = 0f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        k = getResources().getDisplayMetrics().widthPixels / 480f;

        root = findViewById(R.id.root);
        host = (SheetHost) findViewById(R.id.host);
        historyList = (ListView) findViewById(R.id.historyList);
        header = (HistoryHeader) findViewById(R.id.header);
        svExpression = (ExprScrollView) findViewById(R.id.svExpression);
        svResult = (ExprScrollView) findViewById(R.id.svResult);
        tvExpression = (TextView) findViewById(R.id.tvExpression);
        tvResult = (TextView) findViewById(R.id.tvResult);
        pad = (PadView) findViewById(R.id.pad);
        iconHistory = (IconView) findViewById(R.id.iconHistory);
        iconMore = (IconView) findViewById(R.id.iconMore);
        iconExpand = (IconView) findViewById(R.id.iconExpand);

        // слой меню и диалогов поверх всего окна
        overlay = new OverlayView(this);
        ((ViewGroup) findViewById(android.R.id.content)).addView(overlay,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.FILL_PARENT));
        overlay.setCallback(new OverlayView.Callback() {
            public void onMenuItem(int id) {
                if (id == OverlayView.ITEM_CLEAR) {
                    overlay.showDialog(DLG_CLEAR, getString(R.string.clear_history_question),
                            getString(R.string.close), getString(R.string.clear));
                } else {
                    overlay.showDialog(DLG_THEME, getString(R.string.coming_soon),
                            null, getString(R.string.ok));
                }
            }

            public void onMenuProgress(float p) {
                iconMore.setActive(p);
            }

            public void onDialogResult(int dialogId, boolean positive) {
                if (dialogId == DLG_CLEAR && positive) {
                    HistoryStore.clear(MainActivity.this);
                    historyList.setAdapter(new HistoryAdapter(MainActivity.this,
                            HistoryStore.load(MainActivity.this)));
                }
            }
        });

        // Размеры текста сняты со скриншота, переносить на новую строку нельзя
        tvExpression.setTextSize(TypedValue.COMPLEX_UNIT_PX, 60f * k);
        tvResult.setTextSize(TypedValue.COMPLEX_UNIT_PX, 38f * k);
        tvExpression.setEllipsize(null);
        tvResult.setEllipsize(null);
        ascExpr = tvExpression.getPaint().ascent();
        ascRes = tvResult.getPaint().ascent();
        svExpression.setPadding(Math.round(24f * k), 0, Math.round(30f * k), 0);
        svResult.setPadding(Math.round(24f * k), 0, Math.round(30f * k), 0);
        updateDisplayPosition(0f);

        // список истории не должен заезжать под полоску шторки
        host.post(new Runnable() {
            public void run() {
                historyList.setPadding(0, 0, 0, host.stripHeight());
            }
        });

        pad.setDegrees(engine.isDegrees());
        pad.setInverse(engine.isInverse());

        pad.setPadListener(new PadView.PadListener() {
            public void onKey(int key) {
                handleKey(key);
                refresh();
            }

            public void onLongKey(int key) {
                // долгое нажатие на стирание очищает всё
                if (key == PadView.K_BACK) {
                    engine.clear();
                    refresh();
                }
            }

            public void onExpandProgress(float t) {
                iconExpand.setProgress(t);
            }
        });

        iconExpand.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pad.toggle();
            }
        });

        iconHistory.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (host.isOpen()) {
                    closeHistory();
                } else {
                    openHistory();
                }
            }
        });

        iconMore.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int[] a = new int[2];
                int[] b = new int[2];
                root.getLocationInWindow(a);
                iconMore.getLocationInWindow(b);
                overlay.showMenu(b[0] - a[0] + iconMore.getWidth() / 2f,
                        b[1] - a[1] + iconMore.getHeight() / 2f);
            }
        });

        header.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeHistory();
            }
        });

        host.setListener(new SheetHost.Listener() {
            public void onProgress(float p) {
                onHistoryProgress(p);
            }

            public void onSwipeUp() {
                closeHistory();
            }
        });

        refresh();
    }

    @Override
    public void onBackPressed() {
        if (overlay.dismissTop()) {
            return;
        }
        if (host.isOpen()) {
            closeHistory();
        } else {
            super.onBackPressed();
        }
    }

    // --------------------------------------------------------------- история

    private void openHistory() {
        historyList.setAdapter(new HistoryAdapter(this, HistoryStore.load(this)));
        historyList.setVisibility(View.VISIBLE);
        host.setOpen(true);
        historyList.post(new Runnable() {
            public void run() {
                int n = historyList.getCount();
                if (n > 0) {
                    historyList.setSelection(n - 1);
                }
            }
        });
    }

    private void closeHistory() {
        host.setOpen(false);
    }

    /** Вызывается на каждом кадре анимации шторки (p: 0..1). */
    private void onHistoryProgress(float p) {
        historyProgress = p;
        root.setBackgroundColor(Fmt.lerpColor(BG_NORMAL, BG_HISTORY, p));
        iconHistory.setActive(p);
        header.setProgress(p);
        updateDisplayPosition(p);
        if (p <= 0.001f && !host.isOpen()) {
            historyList.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Ставит строки дисплея по базовым линиям со скриншота. Когда история открыта,
     * они опускаются на 40 px, чтобы освободить место для ручки и подписи.
     */
    private void updateDisplayPosition(float p) {
        float shift = 40f * p;
        setTop(svExpression, (72f + shift) * k + ascExpr);
        setTop(svResult, (130f + shift) * k + ascRes);
    }

    private void setTop(View v, float top) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int t = Math.round(top);
        if (lp.topMargin != t) {
            lp.topMargin = t;
            v.setLayoutParams(lp);
        }
    }

    // ----------------------------------------------------------------- кнопки

    private void handleKey(int key) {
        if (key >= 0 && key <= 9) {
            engine.digit((char) ('0' + key));
            return;
        }
        boolean inv = engine.isInverse();
        switch (key) {
        case PadView.K_COMMA:
            engine.comma();
            break;
        case PadView.K_AC:
            engine.clear();
            break;
        case PadView.K_BACK:
            engine.backspace();
            break;
        case PadView.K_PLUS:
            engine.op('+');
            break;
        case PadView.K_MINUS:
            engine.op(CalcEngine.MINUS);
            break;
        case PadView.K_MUL:
            engine.op(CalcEngine.MUL);
            break;
        case PadView.K_DIV:
            engine.op(CalcEngine.DIV);
            break;
        case PadView.K_PERCENT:
            engine.percent();
            break;
        case PadView.K_BRACKET:
            engine.bracket();
            break;
        case PadView.K_EQ:
            engine.equalsPressed();
            String[] h = engine.pollHistory();
            if (h != null) {
                HistoryStore.add(this, h[0], h[1]);
            }
            break;

        // ---- инженерные кнопки
        case PadView.K_SQRT:
            if (inv) {
                engine.square();
            } else {
                engine.function(String.valueOf(CalcEngine.SQRT_CH));
            }
            break;
        case PadView.K_PI:
            engine.constant(CalcEngine.PI_CH);
            break;
        case PadView.K_E:
            engine.constant('e');
            break;
        case PadView.K_POW:
            engine.op('^');
            break;
        case PadView.K_FACT:
            engine.factorialKey();
            break;
        case PadView.K_DEG:
            engine.toggleDegrees();
            pad.setDegrees(engine.isDegrees());
            break;
        case PadView.K_INV:
            engine.toggleInverse();
            pad.setInverse(engine.isInverse());
            break;
        case PadView.K_SIN:
            engine.function(inv ? "asin" : "sin");
            break;
        case PadView.K_COS:
            engine.function(inv ? "acos" : "cos");
            break;
        case PadView.K_TAN:
            engine.function(inv ? "atan" : "tan");
            break;
        case PadView.K_LN:
            if (inv) {
                engine.expPower();
            } else {
                engine.function("ln");
            }
            break;
        case PadView.K_LOG:
            if (inv) {
                engine.tenPower();
            } else {
                engine.function("log");
            }
            break;
        }
    }

    /** Обновляет обе строки дисплея. Текст не обрезается: его можно листать пальцем. */
    private void refresh() {
        tvExpression.setText(Fmt.superscripts(engine.getExpressionText()));
        tvResult.setText(engine.getResultText());
        svExpression.scrollToEndOnLayout();
        svResult.scrollToEndOnLayout();
    }
}
