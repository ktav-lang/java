package lang.ktav.pure;

import lang.ktav.Value;

/**
 * Recursive-descent parser for inline compounds (§ 5.8) and inline
 * scalar collection — the behavioural equivalent of the Rust core's
 * gate scan + top-level split + per-segment parse pipeline.
 *
 * <p>Key behavioural points carried over from the spec / reference core:
 * <ul>
 *   <li>A {@code {}/[} opens a nested compound only at the first
 *       non-whitespace byte of a VALUE (§ 5.8.5); mid-scalar brackets
 *       are literal data.</li>
 *   <li>{@code ::} raw scalars terminate at the first unescaped
 *       {@code ,} / {@code }} / {@code ]} regardless of the enclosing
 *       scope; raw mode makes openers literal but never shields a
 *       closer. A wrong-kind closer terminates the scalar and leaves
 *       the enclosing compound without its own closer — diagnosed as
 *       {@code UnterminatedInlineCompound}, never Malformed.</li>
 *   <li>An unterminated quoted key inside an inline compound swallows
 *       the line: {@code UnterminatedInlineCompound} (§ 6.16 excludes
 *       inline-pair positions).</li>
 *   <li>{@code BadEscapeSequence} is raised during collection, so it
 *       takes precedence over a missing closer (§ 5.2 preamble).</li>
 *   <li>Whitespace is trimmed from raw bytes BEFORE escape processing
 *       (§ 5.8.1); empty pair values are empty Strings, but an empty
 *       ARRAY item position is MalformedInlineCompound (§ 5.8.3).</li>
 * </ul>
 */
final class Inline {

    private Inline() {
    }

    /** A parsed value plus the scan position after it and its terminator. */
    static final class Parsed {
        final Object value; // Value, ObjBuilder, or ArrBuilder
        final int next;     // scan position just past the value
        final byte term;    // ',' '}' ']' or 0 for end-of-line
        final boolean hadEscape; // scalar collections only

        Parsed(Object value, int next, byte term) {
            this(value, next, term, false);
        }

        Parsed(Object value, int next, byte term, boolean hadEscape) {
            this.value = value;
            this.next = next;
            this.term = term;
            this.hadEscape = hadEscape;
        }
    }

    /**
     * Parse one inline value starting at or after {@code from} (a value
     * position inside a compound). Returns the value, the position past
     * it, and the terminator that ended it (0 = end of line).
     */
    static Parsed parseValue(Ctx ctx, int from, int end, int depth) {
        byte[] b = ctx.b;
        int i = Bytes.skipWs(b, from, end);
        if (depth > PureParser.MAX_DEPTH) {
            throw ctx.depthExceeded();
        }
        if (i >= end) {
            // Empty value position — callers normally pre-check; treated
            // as the explicit empty String (§ 5.8.2).
            return new Parsed(new Value.Str(""), end, (byte) 0);
        }
        byte c = b[i];
        if (c == '{') {
            return parseObject(ctx, i, end, depth);
        }
        if (c == '[') {
            return parseArray(ctx, i, end, depth);
        }
        Parsed p = collectScalar(ctx, i, end);
        Value v = scalars(ctx, ((Value.Str) p.value).value(), p.hadEscape);
        return new Parsed(v, p.next, p.term, p.hadEscape);
    }

    private static Value scalars(Ctx ctx, String text, boolean hadEscape) {
        return Scalars.classify(text, hadEscape, false, ctx.line,
                ctx.lineText, ctx.spanStart, ctx.spanEnd, ctx.strict).value;
    }

    /** Parse an inline object body; {@code open} is the index of the '{'.
     *  Returns the builder with {@code next} just past its closing '}'. */
    static Parsed parseObject(Ctx ctx, int open, int end, int depth) {
        byte[] b = ctx.b;
        if (depth > PureParser.MAX_DEPTH) {
            throw ctx.depthExceeded();
        }
        Builders.ObjBuilder out = new Builders.ObjBuilder();
        int i = Bytes.skipWs(b, open + 1, end);
        if (i >= end) {
            throw ctx.unterminatedInline();
        }
        while (true) {
            if (b[i] == '}') {
                return new Parsed(out, i + 1, (byte) 0);
            }
            if (b[i] == ',') {
                throw ctx.malformedInline(
                        "leading or doubled comma at pair position");
            }
            // Pair: key ':' / key '::' value
            Keys.ColonResult cr = Keys.scanColon(b, i, end);
            if (cr.kind == Keys.Colon.UNTERMINATED) {
                throw ctx.unterminatedInline();
            }
            if (cr.kind == Keys.Colon.ABSENT) {
                throw ctx.malformedInline("inline pair has no ':' separator");
            }
            int colon = cr.index;
            java.util.List<String> key = Keys.parseKey(b, i, colon,
                    ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd);
            Parsed p;
            if (colon + 1 < end && b[colon + 1] == ':') {
                // Raw marker: no whitespace required, escapes processed,
                // leading '{'/ '[' literal (§ 5.8.2 / § 4).
                p = collectScalar(ctx, colon + 2, end);
                checkTerm(ctx, p.term, (byte) ']');
                i = p.next;
            } else {
                int v = Bytes.skipWs(b, colon + 1, end);
                if (v >= end || b[v] == ',' || b[v] == '}') {
                    p = new Parsed(new Value.Str(""), v, (byte) 0);
                    i = v;
                } else {
                    p = parseValue(ctx, v, end, depth + 1);
                    checkTerm(ctx, p.term, (byte) ']');
                    i = p.next;
                }
            }
            insert(ctx, out, key, p.value);
            // After the value: ',' or the object's own closer.
            i = Bytes.skipWs(b, i, end);
            if (i >= end) {
                throw ctx.unterminatedInline();
            }
            if (b[i] == ',') {
                i = Bytes.skipWs(b, i + 1, end);
                if (i >= end) {
                    throw ctx.unterminatedInline();
                }
                continue; // trailing comma falls through to the '}' arm
            }
            if (b[i] != '}') {
                throw ctx.malformedInline(
                        "expected ',' or '}' after an inline pair");
            }
        }
    }

    /** Parse an inline array body; {@code open} is the index of the '['.
     *  Returns the builder with {@code next} just past its closing ']'. */
    static Parsed parseArray(Ctx ctx, int open, int end, int depth) {
        byte[] b = ctx.b;
        if (depth > PureParser.MAX_DEPTH) {
            throw ctx.depthExceeded();
        }
        Builders.ArrBuilder out = new Builders.ArrBuilder();
        int i = Bytes.skipWs(b, open + 1, end);
        if (i >= end) {
            throw ctx.unterminatedInline();
        }
        while (true) {
            if (b[i] == ']') {
                return new Parsed(out, i + 1, (byte) 0);
            }
            if (b[i] == ',') {
                throw ctx.malformedInline(
                        "leading or doubled comma — empty inline-array item");
            }
            Parsed p = parseValue(ctx, i, end, depth + 1);
            checkTerm(ctx, p.term, (byte) '}');
            out.items.add(p.value);
            i = Bytes.skipWs(b, p.next, end);
            if (i >= end) {
                throw ctx.unterminatedInline();
            }
            if (b[i] == ',') {
                i = Bytes.skipWs(b, i + 1, end);
                if (i >= end) {
                    throw ctx.unterminatedInline();
                }
                continue;
            }
            if (b[i] != ']') {
                throw ctx.malformedInline(
                        "expected ',' or ']' after an inline item");
            }
        }
    }

    /** A wrong-kind closer at this scope terminates the enclosing compound's own closer scan. */
    private static void checkTerm(Ctx ctx, byte term, byte wrong) {
        if (term == wrong) {
            throw ctx.unterminatedInline();
        }
    }

    /** Insert a parsed value under a (possibly dotted) inline-object key. */
    private static void insert(Ctx ctx, Builders.ObjBuilder out,
            java.util.List<String> key, Object value) {
        Builders.Slot slot = Builders.leafSlot(out, key, key.size() == 1,
                key, ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd);
        slot.set(value);
    }

    /**
     * Collect an inline scalar (or raw-marker scalar) from {@code from}
     * to the first unescaped {@code ,} / {@code }} / {@code ]} or end of
     * line. Escapes are validated during the scan (BadEscapeSequence
     * precedence) and processed after a raw-byte trim (§ 5.8.1).
     */
    static Parsed collectScalar(Ctx ctx, int from, int end) {
        byte[] b = ctx.b;
        int i = from;
        boolean hadEscape = false;
        byte term = 0;
        while (i < end) {
            byte c = b[i];
            if (c == '\\') {
                Escapes.validateAt(b, i, end, ctx.line, ctx.lineText,
                        ctx.spanStart, ctx.spanEnd);
                hadEscape = true;
                i += 2; // named forms advance 2; the u-form advances below
                // Re-scan: the U+XXXX escape needs its full length consumed — but
                // hex digits are never structural, so a conservative
                // skip is safe for terminator finding.
                continue;
            }
            if (c == ',' || c == '}' || c == ']') {
                term = c;
                break;
            }
            i++;
        }
        int[] t = Bytes.trim(b, from, i);
        StringBuilder sb = new StringBuilder(i - t[0]);
        Escapes.decode(b, t[0], t[1], sb, ctx.line, ctx.lineText,
                ctx.spanStart, ctx.spanEnd);
        return new Parsed(new Value.Str(sb.toString()),
                term == 0 ? end : i, term, hadEscape);
    }
}
