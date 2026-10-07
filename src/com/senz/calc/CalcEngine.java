package com.senz.calc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * Логика калькулятора без привязки к Android: ввод выражения и вычисление.
 * Русские строки записаны через юникод-коды, чтобы не зависеть от кодировки Eclipse.
 */
public class CalcEngine {

    public static final char MINUS = '\u2212';
    public static final char MUL = '\u00D7';
    public static final char DIV = '\u00F7';
    public static final char PI_CH = '\u03C0';
    public static final char SQRT_CH = '\u221A';

    private static final int MAX_LEN = 120;
    private static final int MAX_DIGITS = 15;     // максимум цифр при вводе
    private static final int MAX_INT_DIGITS = 30; // целые результаты показываем полностью
    private static final MathContext MC20 = new MathContext(20);
    private static final MathContext MC15 = new MathContext(15);
    private static final BigDecimal HUNDRED = new BigDecimal(100);
    private static final BigDecimal PI = BigDecimal.valueOf(Math.PI);
    private static final BigDecimal E = BigDecimal.valueOf(Math.E);

    /** Названия функций; длинные раньше коротких (asin раньше sin). */
    private static final String[] FUNCS = { "asin", "acos", "atan", "sin", "cos", "tan", "log", "ln" };

    private static final String ERR_FORMAT = "Invalid format";
    private static final String ERR_ZERO = "Can't divide by 0";

    private String expr = "";
    private boolean fresh = false; // только что нажали "=", в выражении лежит результат
    private String error = null;
    private boolean degrees = true;
    private boolean inverse = false;
    private String[] pending = null; // запись для истории после "="

    // ------------------------------------------------------------ режимы

    public boolean isDegrees() {
        return degrees;
    }

    public void toggleDegrees() {
        degrees = !degrees;
    }

    public boolean isInverse() {
        return inverse;
    }

    public void toggleInverse() {
        inverse = !inverse;
    }

    // ---------------------------------------------------------------- ввод

    private void startNewIfFresh() {
        if (fresh) {
            expr = "";
            fresh = false;
        }
    }

    public void digit(char d) {
        error = null;
        startNewIfFresh();
        if (expr.length() >= MAX_LEN) {
            return;
        }
        String cur = currentNumber();
        if (cur.replace(",", "").length() >= MAX_DIGITS) {
            return;
        }
        if (cur.equals("0")) {
            if (d == '0') {
                return;
            }
            expr = expr.substring(0, expr.length() - 1) + d;
        } else {
            expr += d;
        }
    }

    public void comma() {
        error = null;
        startNewIfFresh();
        if (expr.length() >= MAX_LEN) {
            return;
        }
        String cur = currentNumber();
        if (cur.indexOf(',') >= 0) {
            return;
        }
        if (cur.length() == 0) {
            expr += "0,";
        } else {
            expr += ",";
        }
    }

    public void op(char c) {
        error = null;
        fresh = false;
        if (expr.length() == 0) {
            if (c == MINUS) {
                expr = "" + MINUS;
            }
            return;
        }
        if (expr.length() >= MAX_LEN) {
            return;
        }
        char l = last();
        if (l == ',') {
            expr = expr.substring(0, expr.length() - 1);
            l = last();
        }
        if (l == '(') {
            if (c == MINUS) {
                expr += c;
            }
            return;
        }
        if (isOp(l)) {
            if (c == MINUS && (l == MUL || l == DIV || l == '^')) {
                expr += c;
                return;
            }
            String base = expr;
            while (base.length() > 0 && isOp(base.charAt(base.length() - 1))) {
                base = base.substring(0, base.length() - 1);
            }
            if (base.length() == 0 || base.endsWith("(")) {
                if (c == MINUS) {
                    expr = base + c;
                }
                return;
            }
            expr = base + c;
            return;
        }
        expr += c;
    }

    public void percent() {
        error = null;
        fresh = false;
        if (expr.length() < MAX_LEN && isValueEnd(last())) {
            expr += '%';
        }
    }

    public void factorialKey() {
        error = null;
        fresh = false;
        if (expr.length() < MAX_LEN && isValueEnd(last())) {
            expr += '!';
        }
    }

    /** Кнопка "x2" (sqrt с включённой Inv). */
    public void square() {
        error = null;
        fresh = false;
        if (expr.length() < MAX_LEN - 1 && isValueEnd(last())) {
            expr += "^2";
        }
    }

    /** Константа: pi или e. */
    public void constant(char c) {
        error = null;
        startNewIfFresh();
        if (expr.length() < MAX_LEN) {
            expr += c;
        }
    }

    /** Функция: вставляет имя и открывающую скобку, например "sin(". */
    public void function(String name) {
        error = null;
        startNewIfFresh();
        if (expr.length() < MAX_LEN - 6) {
            expr += name + "(";
        }
    }

    /** Кнопка "e^x" (ln с включённой Inv). */
    public void expPower() {
        function("e^");
    }

    /** Кнопка "10^x" (log с включённой Inv). */
    public void tenPower() {
        error = null;
        startNewIfFresh();
        if (expr.length() >= MAX_LEN - 6) {
            return;
        }
        if (last() == ',') {
            expr = expr.substring(0, expr.length() - 1);
        }
        if (isDigit(last())) {
            expr += MUL;
        }
        expr += "10^(";
    }

    /** Кнопка "( )": сама решает, открыть или закрыть скобку. */
    public void bracket() {
        error = null;
        startNewIfFresh();
        if (expr.length() >= MAX_LEN) {
            return;
        }
        int open = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') {
                open++;
            } else if (c == ')') {
                open--;
            }
        }
        char l = last();
        if (open > 0 && isValueEnd(l)) {
            expr += ')';
        } else {
            expr += '(';
        }
    }

    public void backspace() {
        error = null;
        fresh = false;
        if (expr.length() == 0) {
            return;
        }
        // функцию стираем целиком вместе со скобкой: "sin(" -> ""
        for (int i = 0; i < FUNCS.length; i++) {
            if (expr.endsWith(FUNCS[i] + "(")) {
                expr = expr.substring(0, expr.length() - FUNCS[i].length() - 1);
                return;
            }
        }
        expr = expr.substring(0, expr.length() - 1);
    }

    public void clear() {
        error = null;
        fresh = false;
        expr = "";
    }

    public void equalsPressed() {
        error = null;
        String s = clean(expr);
        if (s.length() == 0) {
            return;
        }
        try {
            String res = formatRaw(evaluate(s));
            if (hasOperation(s)) {
                pending = new String[] { s, res };
            }
            expr = res;
            fresh = true;
        } catch (ArithmeticException e) {
            error = ERR_ZERO;
        } catch (RuntimeException e) {
            error = ERR_FORMAT;
        }
    }

    // ------------------------------------------------------------- вывод

    /** Запись {выражение, результат} после успешного "=" (один раз), иначе null. */
    public String[] pollHistory() {
        String[] r = pending;
        pending = null;
        return r;
    }

    /** Верхняя строка (с пробелами между разрядами). */
    public String getExpressionText() {
        return toDisplay(expr);
    }

    /** Внутренняя запись -> красивый текст (sin-1 вместо asin, пробелы в числах). */
    public static String toDisplay(String raw) {
        String s = raw.replace("asin", "sin\u22121").replace("acos", "cos\u22121")
                .replace("atan", "tan\u22121");
        return group(s);
    }

    /** Нижняя зелёная строка: предварительный результат или ошибка. */
    public String getResultText() {
        if (error != null) {
            return error;
        }
        if (fresh) {
            return "";
        }
        String s = clean(expr);
        if (s.length() == 0 || !hasOperation(s)) {
            return "";
        }
        try {
            return group(formatRaw(evaluate(s)));
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------- вспомогательное

    private char last() {
        return expr.length() == 0 ? 0 : expr.charAt(expr.length() - 1);
    }

    private String currentNumber() {
        int i = expr.length();
        while (i > 0) {
            char c = expr.charAt(i - 1);
            if (isDigit(c) || c == ',') {
                i--;
            } else {
                break;
            }
        }
        return expr.substring(i);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isOp(char c) {
        return c == '+' || c == MINUS || c == MUL || c == DIV || c == '^';
    }

    /** Символ, после которого можно ставить %, ! или закрывающую скобку. */
    private static boolean isValueEnd(char c) {
        return isDigit(c) || c == ')' || c == '%' || c == '!' || c == PI_CH || c == 'e';
    }

    /** Убирает в конце операторы, открытые скобки, запятую и имена функций. */
    private static String clean(String s) {
        boolean changed = true;
        while (changed) {
            changed = false;
            int n = s.length();
            while (n > 0 && "+\u2212\u00D7\u00F7^(,".indexOf(s.charAt(n - 1)) >= 0) {
                n--;
                changed = true;
            }
            s = s.substring(0, n);
            if (s.endsWith("\u221A")) {
                s = s.substring(0, s.length() - 1);
                changed = true;
            } else {
                for (int i = 0; i < FUNCS.length; i++) {
                    if (s.endsWith(FUNCS[i])) {
                        s = s.substring(0, s.length() - FUNCS[i].length());
                        changed = true;
                        break;
                    }
                }
            }
        }
        return s;
    }

    /** Есть ли в выражении действие (а не просто одно число). */
    private static boolean hasOperation(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char p = i > 0 ? s.charAt(i - 1) : 0;
            if (isOp(c)) {
                if (i == 0 || p == '(' || p == 'E' || isOp(p)) {
                    continue;
                }
                return true;
            }
            if (c == '%' || c == '!' || c == PI_CH || c == SQRT_CH || (c >= 'a' && c <= 'z')) {
                return true;
            }
            if (c == '(' && i > 0 && p != '(' && !isOp(p)) {
                return true;
            }
            if (isDigit(c) && (p == ')' || p == '%' || p == '!')) {
                return true;
            }
        }
        return false;
    }

    /** Ставит пробелы между разрядами целых чисел. */
    static String group(String s) {
        StringBuilder out = new StringBuilder();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (isDigit(c)) {
                int j = i;
                while (j < n && isDigit(s.charAt(j))) {
                    j++;
                }
                char p = i > 0 ? s.charAt(i - 1) : 0;
                boolean noGroup = p == ',' || p == 'E'
                        || (p == MINUS && i > 1 && s.charAt(i - 2) == 'E');
                String run = s.substring(i, j);
                if (noGroup) {
                    out.append(run);
                } else {
                    int len = run.length();
                    for (int q = 0; q < len; q++) {
                        if (q > 0 && (len - q) % 3 == 0) {
                            out.append(' ');
                        }
                        out.append(run.charAt(q));
                    }
                }
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------ форматирование

    /**
     * Результат в виде строки. Целые числа показываются полностью (до 30 цифр),
     * дробные округляются до 15 значащих цифр, совсем большие и маленькие
     * записываются как 1,5E40.
     */
    static String formatRaw(BigDecimal r) {
        if (r.signum() == 0) {
            return "0";
        }
        BigDecimal x = r.stripTrailingZeros();
        if (x.scale() > 0) {
            x = x.round(MC15).stripTrailingZeros();
        }
        int intDigits = x.precision() - x.scale();
        String res;
        if (x.scale() <= 0) {
            if (intDigits <= MAX_INT_DIGITS) {
                res = x.toBigInteger().toString();
            } else {
                return sci(x);
            }
        } else {
            if (intDigits < -5) {
                return sci(x);
            }
            res = x.toPlainString();
        }
        res = res.replace('.', ',');
        if (res.startsWith("-")) {
            res = MINUS + res.substring(1);
        }
        return res;
    }

    private static String sci(BigDecimal x) {
        x = x.round(MC15).stripTrailingZeros();
        String u = x.unscaledValue().abs().toString();
        int exp = u.length() - 1 - x.scale();
        StringBuilder b = new StringBuilder();
        if (x.signum() < 0) {
            b.append(MINUS);
        }
        b.append(u.charAt(0));
        if (u.length() > 1) {
            b.append(',').append(u.substring(1));
        }
        b.append('E');
        if (exp < 0) {
            b.append(MINUS).append(-exp);
        } else {
            b.append(exp);
        }
        return b.toString();
    }

    // ------------------------------------------------------------ вычисление

    BigDecimal evaluate(String s) {
        Parser p = new Parser(s);
        BigDecimal v = p.expr();
        if (p.i < s.length()) {
            throw new IllegalStateException("bad format");
        }
        return v;
    }

    private static BigDecimal fromDouble(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalStateException("math error");
        }
        return BigDecimal.valueOf(d).round(MC15);
    }

    private BigDecimal sqrt(BigDecimal a) {
        if (a.signum() < 0) {
            throw new IllegalStateException("math error");
        }
        return fromDouble(Math.sqrt(a.doubleValue()));
    }

    private BigDecimal pow(BigDecimal b, BigDecimal e) {
        if (e.signum() == 0) {
            return BigDecimal.ONE;
        }
        boolean isInt = e.stripTrailingZeros().scale() <= 0;
        if (isInt && e.abs().compareTo(new BigDecimal(999)) <= 0) {
            int n = e.intValue();
            if ((long) b.precision() * Math.abs(n) <= 4000) {
                if (n >= 0) {
                    return b.pow(n);
                }
                if (b.signum() == 0) {
                    throw new ArithmeticException("zero");
                }
                return BigDecimal.ONE.divide(b.pow(-n), MC20);
            }
        }
        if (b.signum() == 0 && e.signum() < 0) {
            throw new ArithmeticException("zero");
        }
        return fromDouble(Math.pow(b.doubleValue(), e.doubleValue()));
    }

    private BigDecimal fact(BigDecimal v) {
        if (v.signum() < 0 || v.stripTrailingZeros().scale() > 0
                || v.compareTo(new BigDecimal(1000)) > 0) {
            throw new IllegalStateException("math error");
        }
        int n = v.intValue();
        BigInteger r = BigInteger.ONE;
        for (int k = 2; k <= n; k++) {
            r = r.multiply(BigInteger.valueOf(k));
        }
        return new BigDecimal(r);
    }

    private BigDecimal ln(BigDecimal a) {
        if (a.signum() <= 0) {
            throw new IllegalStateException("math error");
        }
        return fromDouble(Math.log(a.doubleValue()));
    }

    private BigDecimal log10(BigDecimal a) {
        if (a.signum() <= 0) {
            throw new IllegalStateException("math error");
        }
        return fromDouble(Math.log10(a.doubleValue()));
    }

    /** kind: 0 - sin, 1 - cos, 2 - tan. */
    private BigDecimal trig(int kind, BigDecimal a) {
        double d = a.doubleValue();
        double res;
        if (degrees) {
            double m = d % 360.0;
            if (m < 0) {
                m += 360.0;
            }
            if (kind == 0) {
                if (m % 180.0 == 0) return BigDecimal.ZERO;
                if (m == 90.0) return BigDecimal.ONE;
                if (m == 270.0) return BigDecimal.ONE.negate();
                res = Math.sin(Math.toRadians(m));
            } else if (kind == 1) {
                if (m == 90.0 || m == 270.0) return BigDecimal.ZERO;
                if (m == 0.0) return BigDecimal.ONE;
                if (m == 180.0) return BigDecimal.ONE.negate();
                res = Math.cos(Math.toRadians(m));
            } else {
                if (m % 180.0 == 0) return BigDecimal.ZERO;
                if (m == 90.0 || m == 270.0) {
                    throw new IllegalStateException("math error");
                }
                res = Math.tan(Math.toRadians(m));
            }
        } else {
            if (kind == 0) {
                res = Math.sin(d);
            } else if (kind == 1) {
                res = Math.cos(d);
            } else {
                res = Math.tan(d);
                if (Math.abs(res) > 1e15) {
                    throw new IllegalStateException("math error");
                }
            }
            if (Math.abs(res) < 1e-15 && Math.abs(d) > 1e-6) {
                res = 0;
            }
        }
        return fromDouble(res);
    }

    /** kind: 0 - asin, 1 - acos, 2 - atan. */
    private BigDecimal invTrig(int kind, BigDecimal a) {
        double d = a.doubleValue();
        double res;
        if (kind == 0) {
            if (d < -1 || d > 1) throw new IllegalStateException("math error");
            res = Math.asin(d);
        } else if (kind == 1) {
            if (d < -1 || d > 1) throw new IllegalStateException("math error");
            res = Math.acos(d);
        } else {
            res = Math.atan(d);
        }
        if (degrees) {
            res = Math.toDegrees(res);
        }
        return fromDouble(res);
    }

    private BigDecimal apply(String f, BigDecimal a) {
        if (f.equals("sin")) return trig(0, a);
        if (f.equals("cos")) return trig(1, a);
        if (f.equals("tan")) return trig(2, a);
        if (f.equals("asin")) return invTrig(0, a);
        if (f.equals("acos")) return invTrig(1, a);
        if (f.equals("atan")) return invTrig(2, a);
        if (f.equals("ln")) return ln(a);
        if (f.equals("log")) return log10(a);
        throw new IllegalStateException("unknown function");
    }

    /**
     * Разбор с приоритетами (от низшего): + - ; x / и неявное умножение ;
     * унарный минус ; ^ (справа налево) ; % и ! ; скобки, числа, константы, функции.
     * Процент после + и -: 200+10% = 220 (10% от 200).
     * Незакрытые скобки в конце закрываются сами.
     */
    private class Parser {
        final String s;
        int i = 0;
        int pct = 0;

        Parser(String s) {
            this.s = s;
        }

        char peek() {
            return i < s.length() ? s.charAt(i) : 0;
        }

        String functionAt() {
            for (int k = 0; k < FUNCS.length; k++) {
                if (s.startsWith(FUNCS[k] + "(", i)) {
                    return FUNCS[k];
                }
            }
            return null;
        }

        boolean operandStart() {
            char c = peek();
            if (isDigit(c) || c == ',' || c == '(' || c == PI_CH || c == 'e' || c == SQRT_CH) {
                return true;
            }
            return functionAt() != null;
        }

        BigDecimal expr() {
            BigDecimal v = term(null);
            while (true) {
                char c = peek();
                if (c == '+' || c == MINUS) {
                    i++;
                    BigDecimal t = term(v);
                    v = (c == '+') ? v.add(t) : v.subtract(t);
                } else {
                    break;
                }
            }
            return v;
        }

        BigDecimal term(BigDecimal ctx) {
            BigDecimal v = unary();
            boolean single = true;
            boolean hadPercent = pct > 0;
            while (true) {
                char c = peek();
                if (c == MUL || c == DIV) {
                    i++;
                    BigDecimal f = unary();
                    v = (c == MUL) ? v.multiply(f) : v.divide(f, MC20);
                    single = false;
                } else if (operandStart()) {
                    v = v.multiply(unary());
                    single = false;
                } else {
                    break;
                }
            }
            if (ctx != null && single && hadPercent) {
                v = ctx.multiply(v);
            }
            return v;
        }

        BigDecimal unary() {
            boolean neg = false;
            while (peek() == MINUS || peek() == '+') {
                if (peek() == MINUS) {
                    neg = !neg;
                }
                i++;
            }
            BigDecimal v = power();
            return neg ? v.negate() : v;
        }

        BigDecimal power() {
            BigDecimal b = postfix();
            int pc = pct;
            if (peek() == '^') {
                i++;
                BigDecimal e = unary();
                b = pow(b, e);
                pc = 0;
            }
            pct = pc;
            return b;
        }

        BigDecimal postfix() {
            BigDecimal v = primary();
            int count = 0;
            while (peek() == '%' || peek() == '!') {
                if (peek() == '%') {
                    v = v.divide(HUNDRED);
                    count++;
                } else {
                    v = fact(v);
                }
                i++;
            }
            pct = count;
            return v;
        }

        BigDecimal primary() {
            char c = peek();
            if (c == '(') {
                i++;
                BigDecimal v = expr();
                if (peek() == ')') {
                    i++;
                } else if (i < s.length()) {
                    throw new IllegalStateException("bad format");
                }
                return v;
            }
            if (c == PI_CH) {
                i++;
                return PI;
            }
            if (c == 'e') {
                i++;
                return E;
            }
            if (c == SQRT_CH) {
                i++;
                return sqrt(primary());
            }
            String f = functionAt();
            if (f != null) {
                i += f.length();
                return apply(f, primary());
            }
            return number();
        }

        BigDecimal number() {
            int st = i;
            while (isDigit(peek())) {
                i++;
            }
            String ip = s.substring(st, i);
            String fp = "";
            if (peek() == ',') {
                i++;
                int fs = i;
                while (isDigit(peek())) {
                    i++;
                }
                fp = s.substring(fs, i);
            }
            if (ip.length() == 0 && fp.length() == 0) {
                throw new IllegalStateException("bad format");
            }
            if (ip.length() == 0) {
                ip = "0";
            }
            if (fp.length() == 0) {
                fp = "0";
            }
            BigDecimal v = new BigDecimal(ip + "." + fp);
            if (peek() == 'E') {
                i++;
                boolean negExp = false;
                if (peek() == MINUS) {
                    negExp = true;
                    i++;
                } else if (peek() == '+') {
                    i++;
                }
                int es = i;
                while (isDigit(peek())) {
                    i++;
                }
                if (i == es || i - es > 4) {
                    throw new IllegalStateException("bad format");
                }
                int e = Integer.parseInt(s.substring(es, i));
                v = v.scaleByPowerOfTen(negExp ? -e : e);
            }
            return v;
        }
    }
}
