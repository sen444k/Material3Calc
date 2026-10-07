package com.senz.calc;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Строки истории: мелкое выражение сверху и крупный результат снизу, прижаты вправо. */
public class HistoryAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<String[]> items;
    private final float k;

    public HistoryAdapter(Context ctx, List<String[]> items) {
        this.ctx = ctx;
        this.items = items;
        this.k = ctx.getResources().getDisplayMetrics().widthPixels / 480f;
    }

    public int getCount() {
        return items.size();
    }

    public Object getItem(int position) {
        return items.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    private static class Holder {
        ExprScrollView svExpr;
        ExprScrollView svRes;
        TextView tvExpr;
        TextView tvRes;
    }

    private ExprScrollView makeScroll(TextView tv) {
        ExprScrollView sv = new ExprScrollView(ctx);
        sv.setPadding(Math.round(16f * k), 0, Math.round(32f * k), 0);
        sv.addView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private TextView makeText(float sizeDesign, int color) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeDesign * k);
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setEllipsize(null);
        tv.setGravity(Gravity.RIGHT);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            h = new Holder();
            h.tvExpr = makeText(27f, 0xFF8E918F);
            h.tvRes = makeText(42f, 0xFFC4C7C5);
            h.svExpr = makeScroll(h.tvExpr);
            h.svRes = makeScroll(h.tvRes);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Math.round(36f * k), 0, Math.round(19f * k));
            row.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            row.addView(h.svExpr, lp);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp2.topMargin = Math.round(12f * k);
            row.addView(h.svRes, lp2);
            row.setTag(h);
            convertView = row;
        } else {
            h = (Holder) convertView.getTag();
        }

        String[] it = items.get(position);
        h.tvExpr.setText(Fmt.superscripts(CalcEngine.toDisplay(it[0])));
        h.tvRes.setText(CalcEngine.toDisplay(it[1]));
        h.svExpr.scrollToEndOnLayout();
        h.svRes.scrollToEndOnLayout();
        return convertView;
    }
}
