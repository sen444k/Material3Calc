package com.senz.calc;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Долговременная история: хранится в SharedPreferences и переживает
 * закрытие приложения. Формат: строки "выражение TAB результат", разделитель \n.
 */
final class HistoryStore {

    private static final String PREFS = "calc_prefs";
    private static final String KEY = "history";
    private static final int MAX = 300;

    private HistoryStore() {
    }

    /** От старых записей к новым; каждая запись {выражение, результат}. */
    static List<String[]> load(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        if (s.length() == 0) {
            return out;
        }
        String[] lines = s.split("\n");
        for (int i = 0; i < lines.length; i++) {
            int tab = lines[i].indexOf('\t');
            if (tab > 0) {
                out.add(new String[] { lines[i].substring(0, tab), lines[i].substring(tab + 1) });
            }
        }
        return out;
    }

    static void add(Context c, String expr, String result) {
        List<String[]> all = load(c);
        all.add(new String[] { expr, result });
        while (all.size() > MAX) {
            all.remove(0);
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < all.size(); i++) {
            b.append(all.get(i)[0]).append('\t').append(all.get(i)[1]).append('\n');
        }
        SharedPreferences.Editor ed = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        ed.putString(KEY, b.toString());
        ed.commit();
    }

    static void clear(Context c) {
        SharedPreferences.Editor ed = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        ed.remove(KEY);
        ed.commit();
    }
}
