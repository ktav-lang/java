package lang.ktav;

import java.util.List;

/**
 * Thrown when the native library rejects an input — parse failure for
 * {@link Ktav#loads}, render failure for {@link Ktav#dumps}.
 *
 * <p>Errors raised by the native side carry the structured error
 * envelope fields (see {@link lang.ktav.internal.ErrorEnvelope}) as
 * first-class members; {@link #getMessage()} is the core's own
 * rendering, taken verbatim from the envelope's {@code message} field —
 * never raw JSON, and never reassembled from the other fields (a
 * reassembled sentence would not match what every other Ktav binding
 * prints for the same error). Against a native library built before
 * ktav 0.8.0, which never wrote {@code message}, this falls back to a
 * locally-built sentence. The {@link #KtavException(String)} and
 * {@link #KtavException(String, Throwable)} constructors remain for
 * purely internal errors that never travel the envelope channel.
 */
public final class KtavException extends RuntimeException {
    private static final long serialVersionUID = 1L;

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

    /** Internal error — no envelope fields attached. */
    public KtavException(String message) {
        super(message);
        this.error = null;
        this.reason = null;
        this.line = null;
        this.lineText = null;
        this.spanStart = null;
        this.spanEnd = null;
        this.path = null;
        this.body = null;
        this.canonical = null;
        this.specSection = null;
    }

    /** Internal error with a cause — no envelope fields attached. */
    public KtavException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
        this.reason = null;
        this.line = null;
        this.lineText = null;
        this.spanStart = null;
        this.spanEnd = null;
        this.path = null;
        this.body = null;
        this.canonical = null;
        this.specSection = null;
    }

    private KtavException(String message, String error, String reason,
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

    /**
     * Build a {@code KtavException} from a parsed native error envelope.
     * The message is the core's own {@code message} field, taken
     * verbatim — never rebuilt from the other fields, which was this
     * binding's own reconstruction and produced text that didn't match
     * any other language's rendering of the same error. Against a
     * pre-0.8.0 native library, which never wrote {@code message}, this
     * falls back to the old locally-assembled sentence so the exception
     * still carries something readable.
     */
    public static KtavException fromEnvelope(
            lang.ktav.internal.ErrorEnvelope env) {
        String message = env.getMessage();
        if (message == null) {
            message = describe(env);
        }
        return new KtavException(message,
                env.getError(), env.getReason(), env.getLine(),
                env.getLineText(), env.getSpanStart(), env.getSpanEnd(),
                env.getPath(), env.getBody(), env.getCanonical(),
                env.getSpecSection());
    }

    /**
     * Build a {@code KtavException} for a writer-time rejection of an
     * unrepresentable {@link Value} (spec &sect; 5.9.0, required by spec
     * &sect; 8.2). The binding raises these itself — see the
     * {@code WriterPrecheck} walk for why they never reach the native
     * writers — so there is no envelope to parse: the fields are filled
     * in to match exactly what the core's own envelope contract
     * specifies for {@code UnrepresentableAt} (no line/span/body/canonical
     * — those describe source text, and a render refusal has no source
     * text).
     *
     * <p>Package-private on purpose: the public API surface grows only by
     * what callers actually invoke, and nobody outside {@code lang.ktav}
     * constructs exceptions.
     */
    static KtavException unrepresentableAt(
            String reason, List<String> path, String message) {
        return new KtavException(message, "UnrepresentableAt", reason,
                null, null, null, null, List.copyOf(path), null, null,
                "§5.9.0");
    }

    private static String describe(lang.ktav.internal.ErrorEnvelope env) {
        StringBuilder sb = new StringBuilder("Ktav error ").append(env.getError());
        String body = env.getBody();
        if (body != null) {
            sb.append(": ").append(quote(body));
        }
        String canonical = env.getCanonical();
        if (canonical != null) {
            sb.append(" (canonical ").append(quote(canonical)).append(')');
        }
        String reason = env.getReason();
        if (reason != null) {
            sb.append(" (").append(reason).append(')');
        }
        Long line = env.getLine();
        if (line != null) {
            sb.append(" at line ").append(line);
        } else if (env.getSpanStart() != null && env.getSpanEnd() != null) {
            sb.append(" at byte ").append(env.getSpanStart())
                    .append("..").append(env.getSpanEnd());
        }
        List<String> path = env.getPath();
        if (path != null) {
            sb.append(" in path ").append(String.join(" -> ", path));
        }
        String lineText = env.getLineText();
        if (lineText != null) {
            sb.append(" in ").append(quote(lineText));
        }
        String spec = env.getSpecSection();
        if (spec != null) {
            sb.append(" (spec ").append(spec).append(')');
        }
        return sb.toString();
    }

    private static String quote(String s) {
        return '"' + s + '"';
    }

    /** Envelope error class name, or {@code null} for internal errors. */
    public String getError() {
        return error;
    }

    /** Envelope reason code, or {@code null}. */
    public String getReason() {
        return reason;
    }

    /** 1-based source line, or {@code null}. */
    public Long getLine() {
        return line;
    }

    /** Text of the offending source line, or {@code null}. */
    public String getLineText() {
        return lineText;
    }

    /** Byte offset of the error span start, or {@code null}. */
    public Long getSpanStart() {
        return spanStart;
    }

    /** Byte offset of the error span end, or {@code null}. */
    public Long getSpanEnd() {
        return spanEnd;
    }

    /** Exact decoded key segments of the offending path, or {@code null}. */
    public List<String> getPath() {
        return path;
    }

    /** Envelope body (e.g. the offending scalar text), or {@code null}. */
    public String getBody() {
        return body;
    }

    /** Envelope canonical spelling suggestion, or {@code null}. */
    public String getCanonical() {
        return canonical;
    }

    /** Envelope spec section reference (e.g. "§6.1"), or {@code null}. */
    public String getSpecSection() {
        return specSection;
    }
}
