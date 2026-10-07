package com.senz.calc;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;

/** Мелкие помощники для вывода текста. */
final class Fmt {

    private Fmt() {
    }

    /** Делает "−1" в sin−1, cos−1, tan−1 маленьким верхним индексом. */
    static CharSequence superscripts(String text) {
        int idx = text.indexOf("\u22121");
        if (idx < 0) {
            return text;
        }
        SpannableString ss = new SpannableString(text);
        while (idx >= 0) {
            if (idx >= 3) {
                String name = text.substring(idx - 3, idx);
                if (name.equals("sin") || name.equals("cos") || name.equals("tan")) {
                    ss.setSpan(new SuperscriptSpan(), idx, idx + 2,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ss.setSpan(new RelativeSizeSpan(0.6f), idx, idx + 2,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            idx = text.indexOf("\u22121", idx + 2);
        }
        return ss;
    }

    static int lerpColor(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
