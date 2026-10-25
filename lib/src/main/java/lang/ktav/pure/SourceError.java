package lang.ktav.pure;

import java.util.List;

/**
 * Internal parse error carrying the structured error envelope fields
 * (spec § 6). Raised freely by the scanner/parser modules and converted
 * exactly once, at the {@link PureKtav} boundary, into a public
 * {@code KtavException} via {@code KtavException.fromEnvelope}.
 *
 * <p>Field conventions mirror the native envelope
 * ({@code lang.ktav.internal.ErrorEnvelope} / the Rust core's
 * {@code error::envelope}): {@code error} is the § 6 category name,
 * {@code line} is 1-based (absent for {@code UnclosedCompound}, matching
 * the Rust variant), {@code spanStart}/{@code spanEnd} are half-open
 * UTF-8 byte offsets, {@code path} holds decoded key segments for
 * {@code DuplicateKey}/{@code KeyPathConflict} and raw split segments
 * for {@code InvalidKey}, {@code body}/{@code canonical} carry the
 * {@code LossyScalar} / {@code BadEscapeSequence} /
 * {@code MalformedInlineCompound} payloads.
 */
final class SourceError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    final String error;
    final String reason;
    final Long line;
    final String lineText;
    final Long spanStart;
    final Long spanEnd;
    final List<String> path;
    final String body;
    final String canonical;
    final String specSection;

    SourceError(String message, String error, String reason,
            Long line, String lineText, Long spanStart, Long spanEnd,
            List<String> path, String body, String canonical,
            String specSection) {
        super(message);
        this.error = error;
        this.reason = reason;
        this.line = line;
        this.lineText = lineText;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
        this.path = path;
        this.body = body;
        this.canonical = canonical;
        this.specSection = specSection;
    }

    /** Simple positioned parse error (no body/canonical/path/reason). */
    static SourceError syntax(String error, String specSection, int line,
            String lineText, int spanStart, int spanEnd, String message) {
        return new SourceError(message, error, null, (long) line, lineText,
                (long) spanStart, (long) spanEnd, null, null, null,
                specSection);
    }

    /** Error without a line (UnclosedCompound, matching the Rust variant). */
    static SourceError unpositioned(String error, String specSection,
            int spanStart, int spanEnd, String message) {
        return new SourceError(message, error, null, null, null,
                (long) spanStart, (long) spanEnd, null, null, null,
                specSection);
    }

    /**
     * UnclosedCompound with the 0.9 location contract: the span covers
     * the innermost opener through EOF and the line/line_text describe
     * the opener's 1-based line (decoded from the VALID source — the
     * opener offset always sits inside valid UTF-8 here).
     */
    static SourceError unclosedCompound(int openerLine, String openerLineText,
            int spanStart, int spanEnd, String message) {
        return new SourceError(message, "UnclosedCompound", null,
                (long) openerLine, openerLineText,
                (long) spanStart, (long) spanEnd,
                null, null, null, "§6.1");
    }

    /** Error carrying decoded/raw key path segments. */
    static SourceError withPath(String error, String specSection, int line,
            String lineText, int spanStart, int spanEnd, List<String> path,
            String message) {
        return new SourceError(message, error, null, (long) line, lineText,
                (long) spanStart, (long) spanEnd, List.copyOf(path), null,
                null, specSection);
    }

    /** Strict-mode LossyScalar. */
    static SourceError lossyScalar(int line, String lineText,
            int spanStart, int spanEnd, String body, String canonical) {
        return new SourceError(
                "LossyScalar at line " + line + ": '" + body
                        + "' would be canonicalized to '" + canonical + "'",
                "LossyScalar", null, (long) line, lineText,
                (long) spanStart, (long) spanEnd, null, body, canonical,
                "§3.6/§5.2");
    }

    /** InvalidUtf8 over the raw bytes (0.8.1 errata: line + nonempty span). */
    static SourceError invalidUtf8(int line, String lineText,
            int spanStart, int spanEnd) {
        return new SourceError(
                "InvalidUtf8 at line " + line + " (bytes " + spanStart
                        + ".." + spanEnd + ")",
                "InvalidUtf8", null, (long) line, lineText,
                (long) spanStart, (long) spanEnd, null, null, null, "§6.15");
    }
}
