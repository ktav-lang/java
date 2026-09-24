package lang.ktav;

import java.util.ArrayList;
import java.util.List;

/**
 * Writer-side pre-check for the two § 5.9.0 conditions the JSON wire
 * hides from the native writers (spec § 8.2 requires their reason
 * codes): a scalar root, and a non-finite {@link Value.Flt}. Left to the
 * wire, both surface as opaque {@code Message}-class errors with no
 * reason; this class throws the {@code UnrepresentableAt} envelope the
 * core's writers would produce. The walk mirrors the core: root first,
 * object keys pushed onto the path, array items adding no segment, and
 * every wire-visible condition (CRByte, EmptyKeyName, …) left to the
 * native path.
 *
 * <p>Precedence caveat: with both a wire-hidden and a wire-visible
 * offense present, this check reports the hidden one even when the
 * visible node comes first in document order — the write is refused
 * either way.
 */
final class WriterPrecheck {

    private WriterPrecheck() {
    }

    /**
     * Root check, then the node walk — for {@link Ktav#dumps} and
     * {@link Ktav#emitCanonical}.
     *
     * @throws KtavException {@code UnrepresentableAt}: {@code ScalarRoot}
     *     or {@code NonFiniteFloat}
     */
    static void checkRepresentable(Value root) {
        checkRoot(root);
        walk(root, new ArrayList<>());
    }

    /**
     * Root check only, for {@link Ktav#toStringForceStrings}: coercion
     * turns every Float into a String, so {@code NonFiniteFloat} cannot
     * fire there.
     *
     * @throws KtavException {@code UnrepresentableAt}: {@code ScalarRoot}
     */
    static void checkCoercing(Value root) {
        checkRoot(root);
    }

    /**
     * Prepare a tree for {@link Ktav#toStringForceStrings}: the root
     * check, then every Float leaf the wire cannot carry coerced to
     * {@link Value.Str}. The core's force-strings writer accepts such
     * values (rendering the stored text), but their {@code $f} payloads
     * never cross the wire — so coercing here first makes the native
     * writer's output byte-identical to the core's.
     *
     * @param root document root; must be an Object or an Array
     * @return the coerced tree, or {@code root} when nothing was coerced
     * @throws KtavException {@code UnrepresentableAt}: {@code ScalarRoot}
     */
    static Value forCoercingWriter(Value root) {
        checkRoot(root);
        boolean[] changed = {false};
        Value coerced = coerceUncarriableFloats(root, changed);
        return changed[0] ? coerced : root;
    }

    /** Copy-on-write recursion behind {@link #forCoercingWriter}: order preserved, input never mutated. */
    private static Value coerceUncarriableFloats(Value v, boolean[] changed) {
        if (v instanceof Value.Flt f) {
            if (wireCannotCarryFloat(f.text())) {
                changed[0] = true;
                return new Value.Str(f.text());
            }
            return v;
        }
        if (v instanceof Value.Obj o) {
            java.util.LinkedHashMap<String, Value> entries = null;
            for (var e : o.entries().entrySet()) {
                Value before = e.getValue();
                Value after = coerceUncarriableFloats(before, changed);
                if (after != before) {
                    if (entries == null) {
                        entries = new java.util.LinkedHashMap<>(o.entries());
                    }
                    entries.put(e.getKey(), after);
                }
            }
            return entries == null ? v : new Value.Obj(entries);
        }
        if (v instanceof Value.Arr a) {
            List<Value> original = a.items();
            Value[] items = null;
            for (int i = 0; i < original.size(); i++) {
                Value before = original.get(i);
                Value after = coerceUncarriableFloats(before, changed);
                if (after != before) {
                    if (items == null) {
                        items = original.toArray(new Value[0]);
                    }
                    items[i] = after;
                }
            }
            return items == null ? v : new Value.Arr(List.of(items));
        }
        return v;
    }

    /** Spec &sect; 5.9.0: the root must be an Object or an Array. */
    private static void checkRoot(Value root) {
        if (root instanceof Value.Obj || root instanceof Value.Arr) {
            return;
        }
        throw KtavException.unrepresentableAt("ScalarRoot", List.of(),
                "ScalarRoot: the document root is not an Object or an Array (spec § 5.9.0)");
    }

    /**
     * Depth-first walk in document order. Float text the Rust parser
     * refuses altogether passes through — the wire's business, not
     * {@code NonFiniteFloat}.
     */
    private static void walk(Value v, List<String> path) {
        if (v instanceof Value.Flt f) {
            if (isNonFiniteFloatText(f.text())) {
                throw KtavException.unrepresentableAt("NonFiniteFloat", path,
                        "NonFiniteFloat: a Float is NaN or ±Infinity (spec § 5.9.0)"
                                + pathSuffix(path));
            }
        } else if (v instanceof Value.Obj o) {
            for (var e : o.entries().entrySet()) {
                path.add(e.getKey());
                walk(e.getValue(), path);
                path.remove(path.size() - 1);
            }
        } else if (v instanceof Value.Arr a) {
            for (Value item : a.items()) {
                walk(item, path);
            }
        }
    }

    /**
     * True when Rust's {@code f64::from_str} yields a non-finite value
     * (spellings probe-verified against rustc 1.97) — exactly what its
     * writers refuse as {@code NonFiniteFloat}. Java's
     * {@link Double#parseDouble} diverges both ways: it misses bare
     * {@code inf}/{@code infinity}/{@code nan} spellings, and accepts
     * whitespace, {@code f}/{@code d} suffixes and hex floats that Rust
     * rejects — those stay pass-through wire {@code Message}s.
     */
    private static boolean isNonFiniteFloatText(String text) {
        if (!text.equals(text.trim())) {
            return false;
        }
        if (endsWithJavaOnlySuffix(text) || looksLikeHexFloat(text)) {
            return false;
        }
        try {
            double d = Double.parseDouble(text);
            return Double.isNaN(d) || Double.isInfinite(d);
        } catch (NumberFormatException e) {
            // fall through to the spellings parseDouble does not know
        }
        String t = text;
        if (!t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-')) {
            t = t.substring(1);
        }
        String lower = t.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("inf") || lower.equals("infinity")
                || lower.equals("nan");
    }

    /** Java-only trailing {@code f}/{@code F}/{@code d}/{@code D} suffix (bare {@code inf} carved out). */
    private static boolean endsWithJavaOnlySuffix(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        if (last != 'f' && last != 'F' && last != 'd' && last != 'D') {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return !(lower.equals("inf") || lower.equals("+inf") || lower.equals("-inf"));
    }

    /** Rust has no hex-float syntax: after an optional ± sign, 0x/0X is a parse error there. */
    private static boolean looksLikeHexFloat(String text) {
        String t = text;
        if (!t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-')) {
            t = t.substring(1);
        }
        return t.startsWith("0x") || t.startsWith("0X");
    }

    /**
     * True when the wire's {@code $f} validator would refuse this text —
     * it accepts only f64-parseable finite decimals containing a
     * {@code '.'} or an exponent — so the coercing writer must turn it
     * into a {@link Value.Str} to match the core's output.
     */
    private static boolean wireCannotCarryFloat(String text) {
        if (!text.equals(text.trim())) {
            return true;
        }
        if (looksLikeHexFloat(text)) {
            return true;
        }
        if (endsWithJavaOnlySuffix(text)) {
            return true;
        }
        try {
            return !Double.isFinite(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** The {@code at ["a", "b"]} suffix; omitted for an empty path. */
    private static String pathSuffix(List<String> path) {
        if (path.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" at [");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(quoteSegment(path.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Rust string {@code Debug} of one path segment, per Unicode scalar:
     * the usual single-character escapes plus brace escapes (backslash,
     * "u", lowercase hex) for non-printables; lone surrogates — possible
     * only in Java — escape too.
     */
    private static String quoteSegment(String s) {
        StringBuilder sb = new StringBuilder("\"");
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            switch (cp) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\0' -> sb.append("\\0");
                default -> {
                    if (isPrintable(cp)) {
                        sb.appendCodePoint(cp);
                    } else {
                        sb.append("\\u{").append(Integer.toHexString(cp)).append('}');
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Conservative stand-in for Rust printability: control, format,
     * surrogate, private-use, unassigned and separator code points escape.
     */
    private static boolean isPrintable(int cp) {
        int type = Character.getType(cp);
        return type != Character.CONTROL
                && type != Character.FORMAT
                && type != Character.SURROGATE
                && type != Character.PRIVATE_USE
                && type != Character.UNASSIGNED
                && type != Character.LINE_SEPARATOR
                && type != Character.PARAGRAPH_SEPARATOR;
    }
}
