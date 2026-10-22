package lang.ktav.pure;

import java.nio.charset.StandardCharsets;

import lang.ktav.Value;
import lang.ktav.internal.ErrorEnvelope;
import lang.ktav.KtavException;

/**
 * Pure-Java Ktav parser — a byte-level, single-pass implementation of
 * the ktav 0.8.1 specification (§ 3–§ 6), producing the same
 * {@link Value} tree and the same structured error envelope fields as
 * the native {@code ktav_cabi} backend, with no JNI, native library,
 * or external process.
 *
 * <p>{@code byte[]} entries validate UTF-8 strictly BEFORE any grammar
 * processing (§ 6.15 / 0.8.1 errata) and throw {@link KtavException}
 * with error {@code InvalidUtf8}, a non-empty byte span over the
 * malformed sequence, and a line number counted over the raw LF / CR /
 * CRLF terminators. {@link String} entries encode to UTF-8 first, so
 * {@code InvalidUtf8} is unreachable for them — exactly like the
 * native {@code &str} entry points.
 *
 * <p>Declared numeric domains (matching the reference core): i64
 * integers (out-of-range literals fall through to String per § 5.2
 * rule 13) and IEEE 754 binary64 floats with roundTiesToEven
 * (non-finite results fall through to String per rule 14).
 *
 * <p>Nesting depth is bounded by {@link PureParser#MAX_DEPTH}.
 */
public final class PureKtav {

    private PureKtav() {
    }

    /** Parse a Ktav document given as UTF-8 bytes. */
    public static Value loads(byte[] src) {
        return parse(src, false);
    }

    /** Parse a Ktav document with strict numeric spelling checks. */
    public static Value loadsStrict(byte[] src) {
        return parse(src, true);
    }

    /**
     * Parse a Ktav document given as text. The text is encoded to
     * UTF-8 first; Java strings are always valid Unicode, so
     * {@code InvalidUtf8} can never be thrown from this entry point.
     */
    public static Value loads(String src) {
        return loads(encoded(src));
    }

    /** Strict variant of {@link #loads(String)}. */
    public static Value loadsStrict(String src) {
        return loadsStrict(encoded(src));
    }

    /**
     * Render a {@link Value} back to Ktav text. The top-level value must
     * be a {@link Value.Obj} or {@link Value.Arr}. Matches the reference
     * writer's documented layout: 4-space indent, insertion-ordered
     * keys, &sect; 5.9.10 key form selection, &sect; 5.9.7 string forms
     * (the pretty writers prefer the indented stripped multi-line form).
     *
     * <p>Unrepresentable values (spec &sect; 5.9.0) are rejected with
     * the spec's reason codes surfaced through
     * {@link KtavException#getReason()} / {@link KtavException#getPath()}
     * — the FULL seven-code set the core's writers produce:
     * {@code ScalarRoot}, {@code EmptyKeyName}, {@code NonFiniteFloat},
     * {@code CRByte}, {@code BothFormsRequired},
     * {@code TrailingWhitespaceCollision},
     * {@code LeadingWhitespaceCollision}. Emission runs into a local
     * buffer that is discarded on rejection, so no partial output is
     * ever observable.
     *
     * @throws KtavException {@code UnrepresentableAt} on render error
     */
    public static String dumps(Value value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return PureWriter.dumps(value);
    }

    /**
     * Render a {@link Value} with every leaf scalar coerced to a String
     * via the raw {@code ::} marker semantics, following the core's
     * force-strings writer layout. Integers, floats, booleans, and
     * {@code null} are flattened to their textual form; compounds keep
     * their structure. Unlike {@link #dumps(Value)} and
     * {@link #emitCanonical(Value)} this does NOT reject non-finite
     * floats — they are coerced to their textual String form
     * (e.g. {@code f: NaN}); the {@code ScalarRoot} root check still
     * applies.
     *
     * @throws KtavException {@code UnrepresentableAt} on render error
     *     ({@code ScalarRoot}; keys and String bodies as for dumps)
     */
    public static String toStringForceStrings(Value value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return PureWriter.toStringForceStrings(value);
    }

    /**
     * Render a {@link Value} as the deterministic canonical Ktav form
     * (spec &sect; 5.9): LF-only, 4-space indent, insertion order
     * preserved (never convenience-sorted), &sect; 5.9.8 canonical
     * numbers (shortest round-trip decimals; scientific form iff
     * {@code abs >= 1e7} or {@code abs < 1e-2}, signed zero preserved),
     * multi-line strings preferring verbatim {@code ((…))}, no
     * comments. Empty Object root renders as zero bytes, empty Array
     * root as {@code []}.
     *
     * <p>Unrepresentable values (spec &sect; 5.9.0) are rejected with
     * the full seven-code {@code UnrepresentableAt} set before any
     * output is produced — see {@link #dumps(Value)}.
     *
     * @throws KtavException {@code UnrepresentableAt} on render error
     */
    public static String emitCanonical(Value value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return PureWriter.emitCanonical(value);
    }

    /**
     * Parse Ktav source text and immediately re-emit it in canonical
     * form (spec &sect; 5.9), preserving the source's insertion order of
     * object keys — equivalent to {@code emitCanonical(loads(src))}.
     * Comments and blank lines do NOT survive; use
     * {@link #format(String)} for that.
     *
     * @param src Ktav source text
     * @return canonical Ktav source text
     * @throws KtavException when the source is rejected
     */
    public static String canonicalFromSource(String src) {
        return emitCanonical(loads(src));
    }

    /**
     * Comment-preserving formatter over Ktav SOURCE text. Guarantees
     * (matching the native {@code format}):
     * <ul>
     *   <li>Every comment is preserved verbatim (spec &sect; 3.4: a
     *       comment owns a whole line), re-indented to its attachment
     *       point's depth.</li>
     *   <li>Blank lines survive as grouping hints, but runs of 2+ blank
     *       lines collapse to one, and blank padding immediately inside
     *       brackets (or at the very start/end of the document) is
     *       dropped — hence {@code format(format(x)) == format(x)}.</li>
     *   <li>Key order is never changed.</li>
     *   <li>For documents with no comments and no blank lines the result
     *       equals {@code emitCanonical(loads(src))}.</li>
     * </ul>
     *
     * <p>Line endings: the input may use LF, CR, or CRLF; the output is
     * always LF-only (the reference formatter's policy).
     *
     * @param src Ktav source text
     * @return formatted Ktav source text
     * @throws KtavException on parse or render error
     */
    public static String format(String src) {
        try {
            Validated v = validated(encoded(src));
            return PureFormatter.format(v.b, v.start);
        } catch (SourceError e) {
            throw KtavException.fromEnvelope(ErrorEnvelope.of(e.error,
                    e.reason, e.line, e.lineText, e.spanStart, e.spanEnd,
                    e.path, e.body, e.canonical, e.specSection,
                    e.getMessage()));
        }
    }

    /**
     * Version of the pure Ktav backend — the spec version it
     * implements.
     */
    public static String version() {
        return "0.8.1";
    }

    private static byte[] encoded(String src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        return src.getBytes(StandardCharsets.UTF_8);
    }

    /** A validated document plus the offset where the grammar begins. */
    private static final class Validated {
        final byte[] b;
        final int start;

        Validated(byte[] b, int start) {
            this.b = b;
            this.start = start;
        }
    }

    /**
     * Validate UTF-8 (before any grammar processing) and locate exactly
     * one leading byte-order mark (§ 3.1). The ORIGINAL byte array is
     * kept — grammar spans must stay in original raw coordinates — and
     * the grammar loop begins at {@code start} (3 after a BOM, else 0).
     * {@code InvalidUtf8} diagnostics are computed over the raw bytes
     * (0.8.1 errata), before the BOM is ever skipped.
     */
    private static Validated validated(byte[] src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        Utf8.validate(src);
        if (src.length >= 3 && (src[0] & 0xFF) == 0xEF
                && (src[1] & 0xFF) == 0xBB && (src[2] & 0xFF) == 0xBF) {
            return new Validated(src, 3);
        }
        return new Validated(src, 0);
    }

    private static Value parse(byte[] raw, boolean strict) {
        try {
            // UTF-8 validation + BOM location run BEFORE any grammar
            // processing; inside the try so a SourceError from Utf8 is
            // converted at this boundary like every other parse error.
            Validated v = validated(raw);
            return PureParser.parse(v.b, v.start, strict);
        } catch (SourceError e) {
            throw KtavException.fromEnvelope(ErrorEnvelope.of(e.error,
                    e.reason, e.line, e.lineText, e.spanStart, e.spanEnd,
                    e.path, e.body, e.canonical, e.specSection,
                    e.getMessage()));
        }
    }
}
