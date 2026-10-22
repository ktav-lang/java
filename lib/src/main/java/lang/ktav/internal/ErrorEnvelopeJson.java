package lang.ktav.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Jackson streaming codec for native error-envelope JSON. */
final class ErrorEnvelopeJson {
    private static final JsonFactory FACTORY = new JsonFactory();
    private ErrorEnvelopeJson() {}
    static ErrorEnvelope parse(byte[] json) {
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
            String message = null;

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
                        // An explicit JSON null stays null. Boxing the
                        // NULL_SPAN sentinel would surface -1 as a byte
                        // offset and defeat the whole point of the boxed
                        // Long return type.
                        if (span != NULL_SPAN) {
                            spanStart = span[0];
                            spanEnd = span[1];
                        }
                    }
                    case "path" -> path = readPath(p);
                    case "body" -> body = readString(p);
                    case "canonical" -> canonical = readString(p);
                    case "spec_section" -> specSection = readString(p);
                    case "message" -> message = readString(p);
                    default -> {
                        // Forward compatibility: skip fields a newer core
                        // added. Discarding the whole envelope here made
                        // EVERY structured error degrade to the "Message"
                        // fallback as soon as 0.8.0 appended `message`.
                        p.nextToken();
                        p.skipChildren();
                    }
                }
            }
            if (error == null) {
                return fallback(raw);
            }
            return ErrorEnvelope.of(error, reason, line, lineText,
                    spanStart, spanEnd, path, body, canonical, specSection, message);
        } catch (Exception e) {
            return fallback(raw);
        }
    }

    private static ErrorEnvelope fallback(String raw) {
        return ErrorEnvelope.of("Message", null, null, null,
                null, null, null, raw, null, null, null);
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
