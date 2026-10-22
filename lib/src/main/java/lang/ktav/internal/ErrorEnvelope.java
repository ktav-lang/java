package lang.ktav.internal;

import java.util.List;

/**
 * Immutable holder for the JSON error envelope written by the native
 * {@code ktav_cabi} library into {@code out_err} on ANY error from any
 * ABI function.
 *
 * <p>Wire contract — since ktav 0.8.0, ten fields, always present in
 * this order (absent value = explicit JSON {@code null}; {@code
 * message} is never null — every error renders):
 * <pre>
 * {"error":&lt;string class name&gt;,
 *  "reason":&lt;string|null&gt;,
 *  "line":&lt;number|null, 1-based&gt;,
 *  "line_text":&lt;string|null&gt;,
 *  "span":{"start":N,"end":M}|null (byte offsets),
 *  "path":&lt;array of decoded key segments|null&gt;,
 *  "body":&lt;string|null&gt;,
 *  "canonical":&lt;string|null&gt;,
 *  "spec_section":&lt;string|null, like "&sect;6.1"&gt;,
 *  "message":&lt;string, the core's own human-readable rendering&gt;}
 * </pre>
 *
 * <p>Against a pre-0.8.0 native library {@code message} is simply
 * absent — {@link #getMessage()} returns {@code null} in that case, and
 * {@link lang.ktav.KtavException#fromEnvelope} falls back to a locally
 * reconstructed sentence.
 *
 * <p>{@code path} segments are the exact decoded keys: a key literally
 * named {@code "a.b"} is ONE segment, not two.
 *
 * <p>{@link #parse(byte[])} is used by the native backend; pure-Java
 * errors construct this JDK-only holder directly through {@link #of}.
 *
 * <p>Unknown field names are SKIPPED, not treated as a malformed
 * payload. The envelope grows over time (0.8.0 appended {@code
 * message}), and rejecting an unrecognised field would silently
 * downgrade every structured error to the {@code "Message"} fallback
 * against any newer core.
 */
public final class ErrorEnvelope {

    private final String error;
    private final String reason;
    private final Long line;
    private final String lineText;
    private final Long spanStart;
    private final Long spanEnd;
    private final List<String> path;
    private final String body;
    private final String canonical;
    private final String specSection;
    private final String message;

    private ErrorEnvelope(
            String error,
            String reason,
            Long line,
            String lineText,
            Long spanStart,
            Long spanEnd,
            List<String> path,
            String body,
            String canonical,
            String specSection,
            String message) {
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
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public String getReason() {
        return reason;
    }

    public Long getLine() {
        return line;
    }

    public String getLineText() {
        return lineText;
    }

    public Long getSpanStart() {
        return spanStart;
    }

    public Long getSpanEnd() {
        return spanEnd;
    }

    public List<String> getPath() {
        return path;
    }

    public String getBody() {
        return body;
    }

    public String getCanonical() {
        return canonical;
    }

    public String getSpecSection() {
        return specSection;
    }

    /**
     * The core's own human-readable rendering of the error, verbatim.
     * {@code null} against a pre-0.8.0 native library, which never wrote
     * this field.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Build an envelope directly from structured fields. Used by the
     * pure-Java backend ({@code lang.ktav.pure}) so its errors carry
     * exactly the same ten-field contract the native side produces.
     * Field order and semantics mirror the native error-envelope mapping.
     *
     * @param message human-readable rendering (never null)
     */
    public static ErrorEnvelope of(
            String error,
            String reason,
            Long line,
            String lineText,
            Long spanStart,
            Long spanEnd,
            List<String> path,
            String body,
            String canonical,
            String specSection,
            String message) {
        return new ErrorEnvelope(error, reason, line, lineText, spanStart,
                spanEnd, path, body, canonical, specSection, message);
    }

    /**
     * Parse the raw {@code out_err} payload. Never throws — malformed or
     * non-JSON payloads degrade to a fallback envelope with
     * {@code error = "Message"} and the raw text as {@code body}.
     */
    public static ErrorEnvelope parse(byte[] json) {
        return ErrorEnvelopeJson.parse(json);
    }
}
