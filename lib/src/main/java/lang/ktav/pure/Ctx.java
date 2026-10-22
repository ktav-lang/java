package lang.ktav.pure;

/**
 * Per-line parse context threaded through the inline/key/scalar
 * modules: the source bytes, strict flag, and the diagnostics of the
 * line currently being processed (1-based number, raw text, and the
 * trimmed-line byte span used as the error span).
 */
final class Ctx {
    final byte[] b;
    /**
     * First byte index of the document inside {@code b}: 3 after a
     * leading BOM was skipped, else 0. All spans remain ORIGINAL raw
     * offsets into {@code b} (§ 3.1 / 0.8.1 errata).
     */
    final int start;
    final boolean strict;
    int line;
    String lineText;
    int spanStart;
    int spanEnd;

    Ctx(byte[] b, int start, boolean strict) {
        this.b = b;
        this.start = start;
        this.strict = strict;
    }

    SourceError syntax(String error, String spec, String message) {
        return SourceError.syntax(error, spec, line, lineText, spanStart,
                spanEnd, message);
    }

    SourceError unterminatedInline() {
        return syntax("UnterminatedInlineCompound", "§6.11",
                "UnterminatedInlineCompound at line " + line
                        + ": no matching closer on the same line");
    }

    SourceError malformedInline(String detail) {
        return syntax("MalformedInlineCompound", "§6.12",
                "MalformedInlineCompound at line " + line + ": " + detail);
    }

    SourceError depthExceeded() {
        return syntax("Other", null,
                "nesting depth exceeds " + PureParser.MAX_DEPTH);
    }
}
