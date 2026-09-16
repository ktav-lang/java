package lang.ktav.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable holder for the JSON error envelope written by the native
 * {@code ktav_cabi} library into {@code out_err} on ANY error from any
 * ABI function.
 *
 * <p>Wire contract — exactly nine fields, always present in this order
 * (absent value = explicit JSON {@code null}):
 * <pre>
 * {"error":&lt;string class name&gt;,
 *  "reason":&lt;string|null&gt;,
 *  "line":&lt;number|null, 1-based&gt;,
 *  "line_text":&lt;string|null&gt;,
 *  "span":{"start":N,"end":M}|null (byte offsets),
 *  "path":&lt;array of decoded key segments|null&gt;,
 *  "body":&lt;string|null&gt;,
 *  "canonical":&lt;string|null&gt;,
 *  "spec_section":&lt;string|null, like "&sect;6.1"&gt;}
 * </pre>
 *
 * <p>{@code path} segments are the exact decoded keys: a key literally
 * named {@code "a.b"} is ONE segment, not two.
 *
 * <p>Parsed with Jackson streaming ({@code jackson-core} only — no
 * databind), positionally. Defensive by design: if the payload is not
 * valid JSON or not envelope-shaped (e.g. an older native lib that
 * writes plain text), {@link #parse} returns a fallback envelope with
 * {@code error = "Message"} and {@code body} holding the raw payload —
 * it never throws.
 */
public final class ErrorEnvelope {

    private static final JsonFactory FACTORY = new JsonFactory();

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
            String specSection) {
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
     * Parse the raw {@code out_err} payload. Never throws — malformed or
     * non-JSON payloads degrade to a fallback envelope with
     * {@code error = "Message"} and the raw text as {@code body}.
     */
    public static ErrorEnvelope parse(byte[] json) {
        String raw = new String(json, StandardCharsets.UTF_8);
        try (JsonParser p = FACTORY.createParser(json)) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                return fallback(raw);
            }
            String error = null;
            String reason = null;
            Long line = null;
            String lineText = null;
            Long spanStart = null;
            Long spanEnd = null;
            List<String> path = null;
            String body = null;
            String canonical = null;
            String specSection = null;

            JsonToken t;
            while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
                if (t != JsonToken.FIELD_NAME) {
                    return fallback(raw);
                }
                String name = p.currentName();
                switch (name) {
                    case "error" -> error = readString(p);
                    case "reason" -> reason = readString(p);
                    case "line" -> line = readLong(p);
                    case "line_text" -> lineText = readString(p);
                    case "span" -> {
                        long[] span = readSpan(p);
                        if (span == BAD) {
                            return fallback(raw);
                        }
                        spanStart = span[0];
                        spanEnd = span[1];
                    }
                    case "path" -> path = readPath(p);
                    case "body" -> body = readString(p);
                    case "canonical" -> canonical = readString(p);
                    case "spec_section" -> specSection = readString(p);
                    default -> {
                        return fallback(raw);
                    }
                }
            }
            if (error == null) {
                return fallback(raw);
            }
            return new ErrorEnvelope(error, reason, line, lineText,
                    spanStart, spanEnd, path, body, canonical, specSection);
        } catch (Exception e) {
            return fallback(raw);
        }
    }

    private static ErrorEnvelope fallback(String raw) {
        return new ErrorEnvelope("Message", null, null, null,
                null, null, null, raw, null, null);
    }

    private static String readString(JsonParser p) throws java.io.IOException {
        if (p.nextToken() != JsonToken.VALUE_STRING) {
            return null;
        }
        return p.getText();
    }

    private static Long readLong(JsonParser p) throws java.io.IOException {
        JsonToken t = p.nextToken();
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return p.getLongValue();
        }
        return null;
    }

    private static final long[] NULL_SPAN = new long[]{-1L, -1L};
    private static final long[] BAD = new long[]{-2L, -2L};

    /**
     * Read the nested {@code {"start":N,"end":M}} span. Returns
     * {@link #NULL_SPAN} for explicit JSON null and {@link #BAD} if the
     * shape is wrong (triggers the fallback).
     */
    private static long[] readSpan(JsonParser p) throws java.io.IOException {
        JsonToken first = p.nextToken();
        if (first == JsonToken.VALUE_NULL) {
            return NULL_SPAN;
        }
        if (first != JsonToken.START_OBJECT) {
            return BAD;
        }
        long start = Long.MIN_VALUE;
        long end = Long.MIN_VALUE;
        JsonToken t;
        while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
            if (t != JsonToken.FIELD_NAME) {
                return null;
            }
            switch (p.currentName()) {
                case "start" -> {
                    if (p.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                        return BAD;
                    }
                    start = p.getLongValue();
                }
                case "end" -> {
                    if (p.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                        return BAD;
                    }
                    end = p.getLongValue();
                }
                default -> {
                    return BAD;
                }
            }
        }
        if (start == Long.MIN_VALUE || end == Long.MIN_VALUE) {
            return BAD;
        }
        return new long[]{start, end};
    }

    private static List<String> readPath(JsonParser p) throws java.io.IOException {
        if (p.nextToken() != JsonToken.START_ARRAY) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        JsonToken t;
        while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
            if (t != JsonToken.VALUE_STRING) {
                return null;
            }
            segments.add(p.getText());
        }
        return segments;
    }
}
