package lang.ktav.pure;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import lang.ktav.Value;

/**
 * § 5.2 scalar value classification plus § 5.9.8 canonical number
 * rendering and the strict-mode (§ 8.1) {@code LossyScalar} checks.
 *
 * <p>Integer domain: i64 (the reference core's domain) — an
 * out-of-range literal falls through to String. Float domain:
 * binary64, roundTiesToEven (Java {@code Double.parseDouble});
 * overflow to infinity falls through to String, underflow to ±0.0 is
 * an ordinary Float.
 */
final class Scalars {

    private Scalars() {
    }

    /** Classification result: a Value, or one of the opener tokens. */
    static final class Cls {
        static final int NONE = 0;
        static final int OPEN_OBJECT = 1;
        static final int OPEN_ARRAY = 2;
        static final int OPEN_ML_STRIPPED = 3;
        static final int OPEN_ML_VERBATIM = 4;

        final int opener;
        final Value value;

        private Cls(int opener, Value value) {
            this.opener = opener;
            this.value = value;
        }

        static Cls of(Value v) {
            return new Cls(NONE, v);
        }

        static Cls open(int opener) {
            return new Cls(opener, null);
        }
    }

    /**
     * Classify a trimmed scalar body. {@code body} is the decoded,
     * trimmed text ({@code hadEscape} marks bodies that went through
     * § 3.7 processing — any recognised escape forces String, § 5.2).
     * Multi-line pair/array-item bodies pass {@code hadEscape == false}
     * and full openers/compound dispatch applies; inline-scalar callers
     * pre-dispatch raw {@code {}/[} themselves and pass
     * {@code allowOpeners=false} (rules 1–4 never apply to an inline
     * scalar body).
     */
    static Cls classify(String body, boolean hadEscape, boolean allowOpeners,
            int line, String lineText, int spanStart, int spanEnd,
            boolean strict) {
        if (allowOpeners && !hadEscape) {
            if (body.equals("{")) {
                return Cls.open(Cls.OPEN_OBJECT);
            }
            if (body.equals("[")) {
                return Cls.open(Cls.OPEN_ARRAY);
            }
            if (body.equals("(")) {
                return Cls.open(Cls.OPEN_ML_STRIPPED);
            }
            if (body.equals("((")) {
                return Cls.open(Cls.OPEN_ML_VERBATIM);
            }
        }
        if (body.equals("()") || body.equals("(())")) {
            return Cls.of(new Value.Str(""));
        }
        if (body.equals("null")) {
            return Cls.of(Value.Null.NULL);
        }
        if (body.equals("true")) {
            return Cls.of(Value.Bool.TRUE);
        }
        if (body.equals("false")) {
            return Cls.of(Value.Bool.FALSE);
        }
        if (hadEscape) {
            // § 3.7 / § 5.2: a recognised escape anywhere in the body
            // forces String, whatever the decoded bytes resemble.
            return Cls.of(new Value.Str(body));
        }
        if (hasRedundantLeadingZero(body)) {
            return Cls.of(new Value.Str(body));
        }
        Long intValue = tryParseInteger(body);
        if (intValue != null) {
            String canonical = Long.toString(intValue);
            if (strict && !canonical.equals(body)) {
                throw SourceError.lossyScalar(line, lineText, spanStart,
                        spanEnd, body, canonical);
            }
            return Cls.of(new Value.Int(canonical));
        }
        if (isFloatLiteral(body)) {
            double val = parseFloat(body);
            if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                String canonical = canonicalFloat(val);
                if (strict && !canonical.equals(body)) {
                    throw SourceError.lossyScalar(line, lineText, spanStart,
                            spanEnd, body, canonical);
                }
                return Cls.of(new Value.Flt(canonical));
            }
        }
        return Cls.of(new Value.Str(body));
    }

    // ------------------------------------------------------------------
    // § 5.2 rule 13: redundant leading zero
    // ------------------------------------------------------------------

    /**
     * True iff the body is a base-10 digit run whose first digit is
     * {@code 0} with at least one further digit or underscore following
     * (with optional sign): {@code 01234}, {@code -045}, {@code 00},
     * {@code 0_7}. Never true for {@code 0x1A} etc. — the {@code 0}
     * belongs to the base prefix there.
     */
    /** ASCII decimal digit only — § 3.6's digit classes are ASCII, never Unicode. */
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** ASCII digit value in {@code radix}, or -1 (hex letters ASCII-only too). */
    private static int digitValue(char c, int radix) {
        int v;
        if (c >= '0' && c <= '9') {
            v = c - '0';
        } else if (c >= 'a' && c <= 'f') {
            v = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
            v = c - 'A' + 10;
        } else {
            return -1;
        }
        return v < radix ? v : -1;
    }

    static boolean hasRedundantLeadingZero(String s) {
        int n = s.length();
        if (n == 0) {
            return false;
        }
        int i = 0;
        char c0 = s.charAt(0);
        if (c0 == '+' || c0 == '-') {
            i = 1;
        }
        if (i >= n || s.charAt(i) != '0') {
            return false;
        }
        return i + 1 < n
                && (isDigit(s.charAt(i + 1)) || s.charAt(i + 1) == '_');
    }

    // ------------------------------------------------------------------
    // § 3.6 integer literal grammar (i64 domain)
    // ------------------------------------------------------------------

    /**
     * Parse a § 3.6 integer literal, or return null if the grammar does
     * not match or the value overflows i64 (→ String per § 5.2 rule 13).
     */
    static Long tryParseInteger(String s) {
        int n = s.length();
        if (n == 0) {
            return null;
        }
        int i = 0;
        boolean negative = false;
        char c0 = s.charAt(0);
        if (c0 == '-' || c0 == '+') {
            negative = c0 == '-';
            i++;
        }
        if (i >= n) {
            return null;
        }
        // Lowercase-only base prefixes (§ 5.2 matches case-sensitively:
        // `0X1A` / `0O17` / `0B101` are Strings).
        if (s.charAt(i) == '0' && i + 1 < n) {
            char p = s.charAt(i + 1);
            if (p == 'x') {
                return parsePrefixed(s, i + 2, 16, negative);
            }
            if (p == 'o') {
                return parsePrefixed(s, i + 2, 8, negative);
            }
            if (p == 'b') {
                return parsePrefixed(s, i + 2, 2, negative);
            }
        }
        return parseDecimal(s, i, negative);
    }

    private static Long parsePrefixed(String s, int from, int radix,
            boolean negative) {
        int n = s.length();
        if (from >= n) {
            return null;
        }
        long acc = 0;
        boolean prevUnderscore = false;
        boolean anyDigit = false;
        for (int i = from; i < n; i++) {
            char c = s.charAt(i);
            if (c == '_') {
                if (prevUnderscore || !anyDigit) {
                    return null;
                }
                prevUnderscore = true;
                continue;
            }
            int d = digitValue(c, radix);
            if (d < 0) {
                return null;
            }
            prevUnderscore = false;
            anyDigit = true;
            long next = acc * radix + d;
            if (Long.compareUnsigned(next, acc) < 0) {
                return null; // u64 wrap → i64 overflow → String
            }
            acc = next;
        }
        if (prevUnderscore || !anyDigit) {
            return null;
        }
        return finishSigned(acc, negative);
    }

    private static Long parseDecimal(String s, int from, boolean negative) {
        int n = s.length();
        if (from >= n || !isDigit(s.charAt(from))) {
            return null;
        }
        long acc = 0;
        boolean prevUnderscore = false;
        boolean anyDigit = false;
        for (int i = from; i < n; i++) {
            char c = s.charAt(i);
            if (c == '_') {
                if (prevUnderscore || !anyDigit) {
                    return null;
                }
                prevUnderscore = true;
                continue;
            }
            if (c < '0' || c > '9') {
                return null;
            }
            prevUnderscore = false;
            anyDigit = true;
            int d = c - '0';
            long next = acc * 10 + d;
            if (Long.compareUnsigned(next, acc) < 0) {
                return null; // u64 wrap → i64 overflow → String
            }
            acc = next;
        }
        if (prevUnderscore || !anyDigit) {
            return null;
        }
        return finishSigned(acc, negative);
    }

    private static Long finishSigned(long magnitude, boolean negative) {
        if (!negative) {
            if (magnitude < 0) {
                return null; // > i64 range
            }
            return magnitude;
        }
        // -0 normalises to 0; the magnitude 2^63 is Long.MIN_VALUE.
        if (magnitude == 0) {
            return 0L;
        }
        int cmp = Long.compareUnsigned(magnitude, Long.MIN_VALUE);
        if (cmp > 0) {
            return null; // i64 overflow → String
        }
        if (cmp == 0) {
            return Long.MIN_VALUE;
        }
        return -magnitude;
    }

    // ------------------------------------------------------------------
    // § 3.6 float literal grammar (binary64 domain)
    // ------------------------------------------------------------------

    /**
     * True iff the body matches the § 3.6 float grammar (case-sensitive
     * digit scan; underscores between digits only; requires either
     * {@code .} with digits on both sides or an exponent).
     */
    static boolean isFloatLiteral(String s) {
        int n = s.length();
        if (n == 0) {
            return false;
        }
        char c0 = s.charAt(0);
        if (!isDigit(c0) && c0 != '+' && c0 != '-') {
            return false;
        }
        int i = 0;
        if (c0 == '+' || c0 == '-') {
            i = 1;
        }
        int afterInt = scanDecPart(s, i);
        if (afterInt < 0) {
            return false;
        }
        i = afterInt;
        if (i < n && s.charAt(i) == '.') {
            int afterFrac = scanDecPart(s, i + 1);
            if (afterFrac < 0) {
                return false;
            }
            i = afterFrac;
            if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                return scanExponent(s, i + 1) == n;
            }
            return i == n;
        }
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            return scanExponent(s, i + 1) == n;
        }
        return false;
    }

    /** Length-so-far of a {@code dec_part} at {@code from}, or -1 if empty/invalid. */
    private static int scanDecPart(String s, int from) {
        int n = s.length();
        if (from >= n || !isDigit(s.charAt(from))) {
            return -1;
        }
        int i = from + 1;
        boolean prevUnderscore = false;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '_') {
                if (prevUnderscore) {
                    return -1;
                }
                prevUnderscore = true;
                i++;
                continue;
            }
            if (!isDigit(c)) {
                break;
            }
            prevUnderscore = false;
            i++;
        }
        return prevUnderscore ? -1 : i;
    }

    private static int scanExponent(String s, int from) {
        int n = s.length();
        if (from >= n) {
            return -1;
        }
        int i = from;
        char c = s.charAt(i);
        if (c == '+' || c == '-') {
            i++;
        }
        return scanDecPart(s, i);
    }

    /** Parse a body already validated by {@link #isFloatLiteral}. */
    static double parseFloat(String s) {
        if (s.indexOf('_') < 0) {
            return Double.parseDouble(s);
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '_') {
                sb.append(c);
            }
        }
        return Double.parseDouble(sb.toString());
    }

    // ------------------------------------------------------------------
    // § 5.9.8 canonical float rendering
    // ------------------------------------------------------------------

    /**
     * § 5.9.8 canonical text for a finite double. Zero keeps its sign
     * ({@code 0.0} / {@code -0.0} — the zero rule). For a non-zero V,
     * the shortest-round-trip decimal is found by rounding the EXACT
     * binary value ({@code new BigDecimal(v)}) HALF_EVEN to
     * {@code p = 1..17} significant digits and accepting the first p
     * whose decimal string parses back bit-identically — the correctly
     * rounded p-digit candidate is the one nearest to V (ties to an
     * even final digit), so this realises the spec's tie-break
     * chain exactly (nearest to V; ties → even final digit; still
     * tied → smaller {@code (D, k)}). Formatting: scientific (lowercase
     * {@code e}, one leading digit, no {@code +}, no mantissa trailing
     * zeros) iff {@code abs >= 1e7} or {@code abs < 1e-2}; otherwise
     * plain decimal with a forced {@code .0} on whole values.
     */
    static String canonicalFloat(double v) {
        if (v == 0.0) {
            return Double.doubleToLongBits(v) < 0 ? "-0.0" : "0.0";
        }
        BigDecimal exact = new BigDecimal(v);
        BigDecimal chosen = exact; // 17 sig digits always round-trip
        for (int p = 1; p <= 17; p++) {
            BigDecimal cand = exact.round(new MathContext(p,
                    RoundingMode.HALF_EVEN));
            if (Double.parseDouble(cand.toPlainString()) == v) {
                chosen = cand;
                break;
            }
        }
        BigDecimal norm = chosen.stripTrailingZeros();
        String digits = norm.unscaledValue().abs().toString();
        int n = digits.length();
        int k = -norm.scale(); // value = integer(D) × 10^k
        int adjusted = k + n - 1;
        int point = n + k;
        StringBuilder out = new StringBuilder();
        if (v < 0) {
            out.append('-');
        }
        double abs = Math.abs(v);
        if (abs >= 1e7 || abs < 1e-2) {
            out.append(digits.charAt(0));
            if (n > 1) {
                out.append('.').append(digits, 1, n);
            }
            out.append('e').append(adjusted);
            return out.toString();
        }
        if (point <= 0) {
            out.append("0.");
            for (int i = 0; i < -point; i++) {
                out.append('0');
            }
            out.append(digits);
        } else if (point >= n) {
            out.append(digits);
            for (int i = n; i < point; i++) {
                out.append('0');
            }
            out.append(".0");
        } else {
            out.append(digits, 0, point).append('.')
                    .append(digits, point, n);
        }
        return out.toString();
    }
}
