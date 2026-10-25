package lang.ktav.pure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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
        return "0.9.0";
    }

    // ------------------------------------------------------------------
    // Lossless source edits (spec 0.9)
    // ------------------------------------------------------------------

    /**
     * Replace an existing value addressed by the textual dotted path
     * ({@code key1.key2.0.key4}; {@code \.} is a literal dot, {@code \\}
     * a literal backslash, other escapes and empty segments are
     * {@code EditInvalidPath}) with {@code replacement}, splicing only
     * the target's bytes: comments, whitespace, key spelling and order,
     * BOM, EOL style, and dotted/inline/block forms outside the
     * effective edit are preserved. The source is never mutated; a
     * replacement equal to the current value is a byte-identical no-op.
     *
     * <p>Errors surface as {@link KtavException} with
     * {@code getError()} equal to {@code EditInvalidPath} (body: the
     * UTF-8 byte offset inside the path argument),
     * {@code EditNotFound}, or {@code EditUnsupported} (body: the
     * reason). Parse errors of the source are the ordinary 0.9 parser
     * envelopes.
     */
    public static String editValue(String src, String path,
            Value replacement) {
        return editValue(encoded(src), path, replacement);
    }

    /** Byte-level variant of {@link #editValue(String, String, Value)}. */
    public static String editValue(byte[] src, String path,
            Value replacement) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        if (replacement == null) {
            throw new NullPointerException("replacement");
        }
        // Source-first, mirroring the C ABI contract: UTF-8 validation
        // and a full grammar parse run BEFORE the path/transport decode,
        // so a damaged source reports its parse envelope even when the
        // path argument is also malformed.
        Validated v = parseSourceFirst(src);
        replacement = canonicalizeWire(replacement);
        List<String> segments = EditPath.parse(pathBytes(path));
        return applyEdit(v, segments, replacement);
    }

    /**
     * {@link #editValue(String, String, Value)} with pre-decoded
     * literal segments: no additional unescaping is performed. A
     * numeric segment addresses a string key inside an Object and a
     * zero-based ASCII-digit index inside an Array.
     */
    public static String editValueSegments(String src,
            List<String> segments, Value replacement) {
        return editValueSegments(encoded(src), segments, replacement);
    }

    /** Byte-level variant of {@link #editValueSegments(String, List, Value)}. */
    public static String editValueSegments(byte[] src,
            List<String> segments, Value replacement) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        if (segments == null) {
            throw new NullPointerException("segments");
        }
        if (replacement == null) {
            throw new NullPointerException("replacement");
        }
        // Source-first (C ABI contract): the source is validated and
        // parsed before the transport decode (replacement canonicalize
        // and the empty-segments NotFound decision), so a damaged source
        // wins over every transport-level error.
        Validated v = parseSourceFirst(src);
        replacement = canonicalizeWire(replacement);
        if (segments.isEmpty()) {
            throw editFailure(lang.ktav.internal.ErrorEnvelope.of(
                    "EditNotFound", null, null, null, null, null, null,
                    null, null, null,
                    "edit path does not address an existing value"));
        }
        return applyEdit(v, segments, replacement);
    }

    /** Validate UTF-8, locate the BOM, and parse the source — first. */
    private static Validated parseSourceFirst(byte[] src) {
        try {
            Validated v = validated(src);
            PureParser.parse(v.b, v.start, false);
            return v;
        } catch (SourceError e) {
            throw KtavException.fromEnvelope(ErrorEnvelope.of(e.error,
                    e.reason, e.line, e.lineText, e.spanStart, e.spanEnd,
                    e.path, e.body, e.canonical, e.specSection,
                    e.getMessage()));
        }
    }

    /** Splice an already-validated source; converts edit errors. */
    private static String applyEdit(Validated v, List<String> segments,
            Value replacement) {
        try {
            return EditSplice.apply(v.b, v.start, segments, replacement);
        } catch (EditError e) {
            throw editFailure(envelopeFor(e));
        } catch (SourceError e) {
            throw KtavException.fromEnvelope(ErrorEnvelope.of(e.error,
                    e.reason, e.line, e.lineText, e.spanStart, e.spanEnd,
                    e.path, e.body, e.canonical, e.specSection,
                    e.getMessage()));
        }
    }

    /**
     * Canonicalize a replacement the way the C ABI does when it decodes
     * the wire ({@code cabi::wire canonical_integer / canonical_float}):
     * {@link Value.Int} loses any {@code +} sign, redundant leading
     * zeros, and signed zero (string-level — arbitrary precision is
     * preserved, never i64-limited); {@link Value.Flt} must contain a
     * {@code .} or exponent and re-rounds to the crate's canonical
     * shortest form, with a non-finite result passing through verbatim
     * (the writers reject it later, fail closed). An invalid payload is
     * a wire-transport violation: a {@code Message} envelope without an
     * invented source location. Value semantics elsewhere (the records
     * themselves, the dumps guard) are untouched — this runs ONLY at the
     * edit boundary, so {@code new Value.Int("+0001")} over {@code n: 1}
     * is the same byte-identical no-op the native backend produces.
     */
    static Value canonicalizeWire(Value v) {
        if (v instanceof Value.Int i) {
            return new Value.Int(canonicalInteger(i.text()));
        }
        if (v instanceof Value.Flt f) {
            return new Value.Flt(canonicalFloatPayload(f.text()));
        }
        if (v instanceof Value.Obj o) {
            LinkedHashMap<String, Value> out = new LinkedHashMap<>();
            o.entries().forEach((k, val) -> out.put(k, canonicalizeWire(val)));
            return new Value.Obj(out);
        }
        if (v instanceof Value.Arr a) {
            List<Value> out = new ArrayList<>(a.items().size());
            for (Value item : a.items()) {
                out.add(canonicalizeWire(item));
            }
            return new Value.Arr(out);
        }
        return v;
    }

    /** Mirror of {@code canonical_integer}: string-level, not i64-parsed. */
    private static String canonicalInteger(String s) {
        String sign;
        String digits;
        if (s.startsWith("-")) {
            sign = "-";
            digits = s.substring(1);
        } else if (s.startsWith("+")) {
            sign = "";
            digits = s.substring(1);
        } else {
            sign = "";
            digits = s;
        }
        if (digits.isEmpty()
                || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw wireMessage("$i payload not an integer literal: \""
                    + s + "\"");
        }
        String trimmed = digits.replaceFirst("^0+", "");
        return trimmed.isEmpty() ? "0" : sign + trimmed;
    }

    /** Mirror of {@code canonical_float}: f64 parse + canonical shortest form. */
    private static String canonicalFloatPayload(String s) {
        double val;
        try {
            val = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw wireMessage("$f payload not a finite decimal: \""
                    + s + "\"");
        }
        if (s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) {
            throw wireMessage("$f payload must contain '.' or exponent: \""
                    + s + "\"");
        }
        if (!Double.isFinite(val)) {
            // Deliberate wire-only capability: passed through verbatim;
            // the § 5.9.0 writers reject it at render time (fail closed).
            return s;
        }
        return Scalars.canonicalFloat(val);
    }

    private static KtavException wireMessage(String text) {
        return KtavException.fromEnvelope(ErrorEnvelope.of(
                "Message", null, null, null, null, null, null, null, null,
                null, text));
    }

    static byte[] pathBytes(String path) {
        if (path == null) {
            throw new NullPointerException("path");
        }
        return path.getBytes(StandardCharsets.UTF_8);
    }

    private static lang.ktav.internal.ErrorEnvelope envelopeFor(EditError e) {
        return switch (e.code) {
            case INVALID_PATH -> ErrorEnvelope.of(
                    "EditInvalidPath", null, null, null, null, null, null,
                    String.valueOf(e.offset), null, null, e.getMessage());
            case NOT_FOUND -> ErrorEnvelope.of(
                    "EditNotFound", null, null, null, null, null, null,
                    null, null, null, e.getMessage());
            case UNSUPPORTED -> ErrorEnvelope.of(
                    "EditUnsupported", null, null, null, null, null, null,
                    e.reason, null, null, e.getMessage());
        };
    }

    private static KtavException editFailure(
            lang.ktav.internal.ErrorEnvelope env) {
        return KtavException.fromEnvelope(env);
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
