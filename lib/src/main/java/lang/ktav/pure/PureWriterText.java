package lang.ktav.pure;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Shared text-rendering primitives for the pure writers and the
 * formatter: § 5.9.7 String form selection (pair and item context,
 * multi-line form choice and the § 5.9.7 collision rejections),
 * § 5.9.5/§ 5.9.6 raw-marker prediction, § 5.9.0 float checks and
 * § 5.9.8 canonical float rendering, § 5.9.10 key form selection and
 * re-escaping, and indentation. Behavioural port of the Rust core's
 * {@code render::helpers} + {@code render::canonical::strings/num}.
 *
 * <p>Rejections are raised as {@link PureWriter.Unrep} without a path;
 * the document-level drivers attach the decoded key path.
 */
final class PureWriterText {

    private PureWriterText() {
    }

    // ------------------------------------------------------------------
    // String emission — pair and item context
    // ------------------------------------------------------------------

    /** Canonical-mode String emission, shared with the formatter. */
    static void emitCanonicalStringInPair(String s, int indent,
            StringBuilder out) {
        emitStringStatic(s, indent, true, out);
    }

    /** Canonical-mode String item emission, shared with the formatter. */
    static void emitCanonicalStringAsItem(String s, int indent,
            boolean isRootArrayFirst, StringBuilder out) {
        emitItemStatic(s, indent, isRootArrayFirst, true, out);
    }

    static void emitStringStatic(String s, int indent,
            boolean canonical, StringBuilder out) {
        if (s.indexOf('\r') >= 0) {
            throw new PureWriter.Unrep("CRByte");
        }
        if (canonical) {
            if (s.isEmpty()) {
                out.append(":\n");
                return;
            }
            if (stringNeedsMultiline(s)) {
                emitMultilineCanonical(s, indent, true, out);
                return;
            }
            out.append(needsRawMarker(s) ? ":: " : ": ").append(s)
                    .append('\n');
        } else {
            if (stringNeedsMultiline(s)) {
                emitMultilinePretty(s, indent, true, out);
                return;
            }
            out.append(needsRawMarker(s) ? ":: " : ": ").append(s)
                    .append('\n');
        }
    }

    static void emitItemStatic(String s, int indent,
            boolean isRootArrayFirst, boolean canonical,
            StringBuilder out) {
        if (s.indexOf('\r') >= 0) {
            throw new PureWriter.Unrep("CRByte");
        }
        if (canonical) {
            if (s.isEmpty()) {
                out.append("::\n");
                return;
            }
            if (stringNeedsMultiline(s)) {
                emitMultilineCanonical(s, indent, false, out);
                return;
            }
        } else {
            if (stringNeedsMultiline(s)) {
                emitMultilinePretty(s, indent, false, out);
                return;
            }
            if (s.isEmpty()) {
                out.append("::\n");
                return;
            }
        }
        if (itemNeedsRawMarker(s)
                || (isRootArrayFirst
                        && (bareItemIsPairCandidate(s)
                                || s.startsWith("\uFEFF")))) {
            out.append(":: ");
        }
        out.append(s).append('\n');
    }

    private static void emitMultilineCanonical(String s, int indent,
            boolean isPair, StringBuilder out) {
        if (chooseMultilineForm(s, false) == MultilineForm.VERBATIM) {
            out.append(isPair ? ": ((\n" : "((\n");
            out.append(s);
            out.append('\n');
            pushIndent(out, indent);
            out.append("))\n");
        } else {
            out.append(isPair ? ": (\n" : "(\n");
            out.append(s);
            out.append('\n');
            pushIndent(out, indent);
            out.append(")\n");
        }
    }

    /** Pretty multi-line body: stripped preferred, content indented. */
    private static void emitMultilinePretty(String s, int indent,
            boolean isPair, StringBuilder out) {
        if (chooseMultilineForm(s, true) == MultilineForm.STRIPPED) {
            out.append(isPair ? ": (\n" : "(\n");
            int contentIndent = indent + 1;
            // Rust `s.split('\n')` keeps the trailing empty segment.
            String[] lines = s.split("\n", -1);
            for (String line : lines) {
                if (!line.isEmpty()) {
                    pushIndent(out, contentIndent);
                    out.append(line);
                }
                out.append('\n');
            }
            pushIndent(out, indent);
            out.append(")\n");
        } else {
            // Exactly one '\n' after s: a trailing newline in s becomes
            // a blank content line, preserving it through the verbatim
            // join round-trip.
            out.append(isPair ? ": ((\n" : "((\n");
            out.append(s);
            out.append('\n');
            pushIndent(out, indent);
            out.append("))\n");
        }
    }

    // ------------------------------------------------------------------
    // § 5.9.0 / § 5.9.7 — multi-line form choice and collisions
    // ------------------------------------------------------------------

    enum MultilineForm {
        STRIPPED, VERBATIM
    }

    /**
     * Pick the multi-line form that reproduces {@code s} byte-for-byte
     * on re-parse, or throw the matching § 5.9.7 collision reason — a
     * direct port of the Rust core's {@code choose_multiline_form}.
     */
    static MultilineForm chooseMultilineForm(String s,
            boolean preferStripped) {
        boolean soleSingle = false;
        boolean soleDouble = false;
        boolean wsOnlyLine = false;
        boolean indentedLine = false;
        boolean trailingWsLine = false;
        int n = s.length();
        int i = 0;
        while (i <= n) {
            int nl = s.indexOf('\n', i);
            int end = nl < 0 ? n : nl;
            String line = s.substring(i, end);
            String trimmed = trimInlineWs(line);
            if (trimmed.equals(")")) {
                soleSingle = true;
            } else if (trimmed.equals("))")) {
                soleDouble = true;
            }
            if (!line.isEmpty()) {
                if (trimmed.isEmpty()) {
                    wsOnlyLine = true;
                } else if (isInlineWs(line.codePointAt(0))) {
                    indentedLine = true;
                }
                if (trimInlineEndWs(line).length() != line.length()) {
                    trailingWsLine = true;
                }
            }
            if (nl < 0) {
                break;
            }
            i = nl + 1;
        }

        boolean strippedSafe = !soleSingle && !wsOnlyLine && !indentedLine
                && !trailingWsLine;
        boolean verbatimOk = !soleDouble;

        if (preferStripped && strippedSafe) {
            return MultilineForm.STRIPPED;
        }
        if (verbatimOk) {
            return MultilineForm.VERBATIM;
        }
        boolean hasCommonIndent = commonLeadingWhitespacePrefixNonzero(s);
        boolean strippedLossless = !soleSingle && !wsOnlyLine
                && !hasCommonIndent && !trailingWsLine;
        if (strippedLossless) {
            return MultilineForm.STRIPPED;
        }
        if (soleSingle) {
            throw new PureWriter.Unrep("BothFormsRequired");
        }
        if (trailingWsLine) {
            throw new PureWriter.Unrep("TrailingWhitespaceCollision");
        }
        throw new PureWriter.Unrep("LeadingWhitespaceCollision");
    }

    /** § 5.6 common leading-whitespace prefix across non-blank lines, nonzero test. */
    private static boolean commonLeadingWhitespacePrefixNonzero(String s) {
        int[] firstRun = null;
        int n = s.length();
        int i = 0;
        while (i <= n) {
            int nl = s.indexOf('\n', i);
            int end = nl < 0 ? n : nl;
            String line = s.substring(i, end);
            if (!trimKtavWs(line).isEmpty()) {
                int[] run = leadingInlineWsRun(line);
                if (firstRun == null) {
                    firstRun = run;
                } else {
                    int cap = Math.min(firstRun.length, run.length);
                    int k = 0;
                    while (k < cap && firstRun[k] == run[k]) {
                        k++;
                    }
                    if (k == 0) {
                        return false;
                    }
                }
            }
            if (nl < 0) {
                break;
            }
            i = nl + 1;
        }
        return firstRun != null && firstRun.length > 0;
    }

    /** Code points of the leading inline-whitespace run of {@code line}. */
    private static int[] leadingInlineWsRun(String line) {
        int i = 0;
        int count = 0;
        while (i < line.length()) {
            int cp = line.codePointAt(i);
            if (!isInlineWs(cp)) {
                break;
            }
            i += Character.charCount(cp);
            count++;
        }
        int[] out = new int[count];
        i = 0;
        for (int k = 0; k < count; k++) {
            int cp = line.codePointAt(i);
            out[k] = cp;
            i += Character.charCount(cp);
        }
        return out;
    }

    private static String trimInlineWs(String s) {
        int from = 0;
        int to = s.length();
        while (from < to && isInlineWs(s.codePointAt(from))) {
            from += Character.charCount(s.codePointAt(from));
        }
        while (to > from) {
            int cp = s.codePointBefore(to);
            if (!isInlineWs(cp)) {
                break;
            }
            to -= Character.charCount(cp);
        }
        return s.substring(from, to);
    }

    private static String trimInlineEndWs(String s) {
        int to = s.length();
        while (to > 0) {
            int cp = s.codePointBefore(to);
            if (!isInlineWs(cp)) {
                break;
            }
            to -= Character.charCount(cp);
        }
        return s.substring(0, to);
    }

    private static String trimKtavWs(String s) {
        int from = 0;
        int to = s.length();
        while (from < to && isKtavWs(s.codePointAt(from))) {
            from += Character.charCount(s.codePointAt(from));
        }
        while (to > from) {
            int cp = s.codePointBefore(to);
            if (!isKtavWs(cp)) {
                break;
            }
            to -= Character.charCount(cp);
        }
        return s.substring(from, to);
    }

    // ------------------------------------------------------------------
    // § 3.3 whitespace (char view) — full set and inline view
    // ------------------------------------------------------------------

    private static boolean isKtavWs(int cp) {
        switch (cp) {
            case 0x09: case 0x0A: case 0x0B: case 0x0C: case 0x0D:
            case 0x20:
                return true;
            default:
                return cp == 0x85 || cp == 0xA0 || cp == 0x1680
                        || (cp >= 0x2000 && cp <= 0x200A)
                        || cp == 0x2028 || cp == 0x2029 || cp == 0x202F
                        || cp == 0x205F || cp == 0x3000;
        }
    }

    private static boolean isInlineWs(int cp) {
        return cp != 0x0A && cp != 0x0D && isKtavWs(cp);
    }

    // ------------------------------------------------------------------
    // § 5.9.5 / § 5.9.6 — would the parser re-classify this body?
    // ------------------------------------------------------------------

    /** True when a one-line pair body must take the {@code ::} marker. */
    static boolean needsRawMarker(String s) {
        if (s.isEmpty()) {
            return false;
        }
        char c0 = s.charAt(0);
        if (c0 == '{' || c0 == '[') {
            return true;
        }
        if (c0 == ' ' || c0 == '\t') {
            String t = s;
            while (!t.isEmpty() && isInlineWs(t.codePointAt(0))) {
                t = t.substring(Character.charCount(t.codePointAt(0)));
            }
            return t.startsWith("{") || t.startsWith("[")
                    || needsRawMarkerContent(t);
        }
        return needsRawMarkerContent(s);
    }

    private static boolean needsRawMarkerContent(String s) {
        if (s.equals("null") || s.equals("true") || s.equals("false")
                || s.equals("(") || s.equals("((") || s.equals("()")
                || s.equals("(())")) {
            return true;
        }
        // § 5.2 rules 13–14: the number grammars are checked
        // syntactically (no i64 range, no leading-zero rule) — the
        // re-parse outcome is what matters, and an out-of-range or
        // zero-prefixed literal would STILL come back a String… but the
        // core marks grammar matches with `::` regardless, and so do we.
        return matchesIntegerGrammar(s) || Scalars.isFloatLiteral(s);
    }

    /** § 3.6 integer grammar, syntax only (no i64 range check). */
    private static boolean matchesIntegerGrammar(String s) {
        int n = s.length();
        if (n == 0) {
            return false;
        }
        int i = 0;
        char c0 = s.charAt(0);
        if (c0 == '+' || c0 == '-') {
            i++;
        }
        if (i >= n) {
            return false;
        }
        if (s.charAt(i) == '0' && i + 1 < n) {
            char p = s.charAt(i + 1);
            if (p == 'x') {
                return checkPrefixedDigits(s, i + 2, 16);
            }
            if (p == 'o') {
                return checkPrefixedDigits(s, i + 2, 8);
            }
            if (p == 'b') {
                return checkPrefixedDigits(s, i + 2, 2);
            }
        }
        return checkDecimalDigits(s, i);
    }

    private static boolean checkPrefixedDigits(String s, int from,
            int radix) {
        int n = s.length();
        if (from >= n) {
            return false;
        }
        if (digitValue(s.charAt(from), radix) < 0) {
            return false;
        }
        boolean prevUnderscore = false;
        for (int i = from + 1; i < n; i++) {
            char c = s.charAt(i);
            if (c == '_') {
                if (prevUnderscore) {
                    return false;
                }
                prevUnderscore = true;
                continue;
            }
            prevUnderscore = false;
            if (digitValue(c, radix) < 0) {
                return false;
            }
        }
        return !prevUnderscore;
    }

    private static boolean checkDecimalDigits(String s, int from) {
        int n = s.length();
        if (from >= n || !isAsciiDigit(s.charAt(from))) {
            return false;
        }
        boolean prevUnderscore = false;
        for (int i = from + 1; i < n; i++) {
            char c = s.charAt(i);
            if (c == '_') {
                if (prevUnderscore) {
                    return false;
                }
                prevUnderscore = true;
                continue;
            }
            prevUnderscore = false;
            if (!isAsciiDigit(c)) {
                return false;
            }
        }
        return !prevUnderscore;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

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

    /**
     * Item-context variant: a bare array-item line has extra collisions
     * — a leading {@code ##} is a comment line, a leading {@code ::} is
     * the raw marker itself, and a sole {@code ]} / {@code }} closes the
     * enclosing compound.
     */
    static boolean itemNeedsRawMarker(String s) {
        return needsRawMarker(s) || s.startsWith("##")
                || s.startsWith("::") || s.equals("]") || s.equals("}");
    }

    /**
     * § 5.9.6 first-item exclusion: does the bare body satisfy § 5.0.1
     * rule 6's phase-1 pair-candidate test? Delegates to the same
     * quote-aware separator scan the parser uses ({@link Keys#scanColon}
     * over the body's UTF-8 bytes); an unterminated leading quote is
     * NOT a pair candidate.
     */
    static boolean bareItemIsPairCandidate(String body) {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        Keys.ColonResult cr = Keys.scanColon(b, 0, b.length);
        if (cr.kind != Keys.Colon.FOUND) {
            return false;
        }
        int ci = cr.index;
        int keyEnd = Bytes.trimEnd(b, 0, ci);
        if (keyEnd <= 0) {
            return false;
        }
        if (ci + 1 < b.length && b[ci + 1] == ':') {
            return true; // `key::` — pair shape even glued
        }
        return ci + 1 >= b.length || Bytes.wsAt(b, ci + 1, b.length) > 0;
    }

    /** § 5.9.7 guard: edge § 3.3 whitespace or a control byte (≠ TAB). */
    private static boolean stringNeedsMultiline(String s) {
        int n = s.length();
        if (n == 0) {
            return false;
        }
        if (isKtavWs(s.codePointAt(0)) || isKtavWs(s.codePointBefore(n))) {
            return true;
        }
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\t') {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // § 5.9.0 float checks / § 5.9.8 canonical float
    // ------------------------------------------------------------------

    /**
     * Rust {@code f64::from_str} semantics: returns the parsed value,
     * or {@code null} when the text is not an f64 lexical form at all
     * (whitespace-padded, Java-only suffixes, hex floats — the same
     * exclusions the binding's {@code WriterPrecheck} documents).
     */
    private static Double parseRustF64(String text) {
        if (!text.equals(text.trim())) {
            return null;
        }
        if (endsWithJavaOnlySuffix(text) || looksLikeHexFloat(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            // fall through to the spellings parseDouble does not know
        }
        String t = text;
        boolean negative = false;
        if (!t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-')) {
            negative = t.charAt(0) == '-';
            t = t.substring(1);
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.equals("inf") || lower.equals("infinity")) {
            return negative ? Double.NEGATIVE_INFINITY
                    : Double.POSITIVE_INFINITY;
        }
        if (lower.equals("nan")) {
            return Double.NaN;
        }
        return null;
    }

    private static boolean endsWithJavaOnlySuffix(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        if (last != 'f' && last != 'F' && last != 'd' && last != 'D') {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return !(lower.equals("inf") || lower.equals("+inf")
                || lower.equals("-inf"));
    }

    private static boolean looksLikeHexFloat(String text) {
        String t = text;
        if (!t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-')) {
            t = t.substring(1);
        }
        return t.startsWith("0x") || t.startsWith("0X");
    }

    static void rejectNonFinite(String text) {
        Double d = parseRustF64(text);
        if (d != null && (d.isNaN() || d.isInfinite())) {
            throw new PureWriter.Unrep("NonFiniteFloat");
        }
    }

    /**
     * § 5.9.8 canonical float text for the canonical writer: EVERY
     * finite payload is re-rendered through the canonicalizer (so a
     * constructed {@code Value.Flt("1.50")} canonicalizes to "1.5" and
     * {@code "5e3"} to "5000.0"; the zero rule gives "-0.00" →
     * "-0.0"), a non-finite payload is a {@code NonFiniteFloat}
     * rejection, and an unparseable payload passes through (outside the
     * Float domain, not a § 5.9.0 code).
     */
    static String canonicalFloatChecked(String text) {
        Double d = parseRustF64(text);
        if (d == null) {
            return text;
        }
        if (d.isNaN() || d.isInfinite()) {
            throw new PureWriter.Unrep("NonFiniteFloat");
        }
        return Scalars.canonicalFloat(d);
    }

    // ------------------------------------------------------------------
    // § 5.9.10 — key form selection and re-escaping
    // ------------------------------------------------------------------

    /**
     * Emit a (flat, single-segment) key choosing bare or quoted form per
     * § 5.9.10 rules (a)–(d). One pass over the key decides the form
     * and, for the bare form, whether any byte needs rewriting.
     */
    static void pushEscapedKeySegment(String key, boolean rootFirstKey,
            StringBuilder out) {
        if (key.isEmpty()) {
            return; // unreachable: EmptyKeyName is checked by callers
        }
        int firstCp = key.codePointAt(0);
        // Rules (b)/(c)/(d) read the leading code point(s) only.
        if (firstCp == '"' || firstCp == '\'' || firstCp == '`'
                || (rootFirstKey && firstCp == 0xFEFF)
                || key.startsWith("##")) {
            pushQuotedKey(key, out);
            return;
        }
        // (a) edge whitespace — except LF/CR, which a quoted segment
        // never admits raw, so quoting buys nothing for them.
        int lastCp = key.codePointBefore(key.length());
        if ((isInlineWs(firstCp) && firstCp != '\n' && firstCp != '\r')
                || (isInlineWs(lastCp) && lastCp != '\n'
                        && lastCp != '\r')) {
            pushQuotedKey(key, out);
            return;
        }
        boolean needsRewrite = false;
        int i = 0;
        while (i < key.length()) {
            int cp = key.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '.' || cp == ':' || cp == ',' || cp == '{'
                    || cp == '}' || cp == '[' || cp == ']' || cp == '('
                    || cp == ')') {
                // (a) a structural byte anywhere → quoted form.
                pushQuotedKey(key, out);
                return;
            }
            if (cp == '\\' || cp == '\n' || cp == '\r' || cp == 0x7F) {
                needsRewrite = true;
            } else if (cp < 0x20 && cp != '\t' && cp != 0x0B
                    && cp != 0x0C) {
                needsRewrite = true;
            }
        }
        if (needsRewrite) {
            pushBareKeyEscaped(key, out);
        } else {
            out.append(key);
        }
    }

    /** U+XXXX escape with uppercase hex (§ 3.7.1), four digits minimum. */
    private static void pushUnicodeEscape(StringBuilder out, int cp) {
        out.append("\\u")
                .append(String.format(Locale.ROOT, "%04X", cp));
    }

    /** Bare form (§ 5.9.10 bullet recipe). Called only when a rewrite is needed. */
    private static void pushBareKeyEscaped(String key, StringBuilder out) {
        int lastIdx = key.offsetByCodePoints(key.length(), -1);
        int i = 0;
        while (i < key.length()) {
            int cp = key.codePointAt(i);
            boolean atEdge = i == 0 || i == lastIdx;
            i += Character.charCount(cp);
            if (cp == '\\') {
                out.append("\\\\");
            } else if (cp == '\n') {
                out.append("\\n");
            } else if (cp == '\r') {
                out.append("\\r");
            } else if (isKtavWs(cp)) {
                if (atEdge) {
                    pushUnicodeEscape(out, cp);
                } else {
                    out.appendCodePoint(cp);
                }
            } else if (cp == '.') {
                out.append("\\.");
            } else if (cp == ':') {
                out.append("\\:");
            } else if (cp == ',') {
                out.append("\\,");
            } else if (cp == '{') {
                out.append("\\{");
            } else if (cp == '}') {
                out.append("\\}");
            } else if (cp == '[') {
                out.append("\\[");
            } else if (cp == ']') {
                out.append("\\]");
            } else if (cp < 0x20 || cp == 0x7F) {
                pushUnicodeEscape(out, cp);
            } else {
                out.appendCodePoint(cp);
            }
        }
    }

    /** Quoted form (§ 5.9.10): {@code "} delimiter, minimal escaping. */
    private static void pushQuotedKey(String key, StringBuilder out) {
        out.append('"');
        int i = 0;
        while (i < key.length()) {
            int cp = key.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '"') {
                out.append("\\\"");
            } else if (cp == '\\') {
                out.append("\\\\");
            } else if (cp == '\n') {
                out.append("\\n");
            } else if (cp == '\r') {
                out.append("\\r");
            } else if ((cp < 0x20 && cp != '\t' && cp != 0x0B
                    && cp != 0x0C) || cp == 0x7F) {
                pushUnicodeEscape(out, cp);
            } else {
                out.appendCodePoint(cp);
            }
        }
        out.append('"');
    }

    // ------------------------------------------------------------------
    // Shared emitters for the formatter (canonical-mode only)
    // ------------------------------------------------------------------

    /** {@code level * 4} spaces via slice-copy chunks (amortized linear). */
    static void pushIndent(StringBuilder out, int level) {
        final String spaces = "                                "
                + "                                "; // 64
        int remaining = level * 4;
        while (remaining > 0) {
            int chunk = Math.min(remaining, spaces.length());
            out.append(spaces, 0, chunk);
            remaining -= chunk;
        }
    }
}
