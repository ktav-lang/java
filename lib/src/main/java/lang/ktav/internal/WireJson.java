package lang.ktav.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import lang.ktav.KtavException;
import lang.ktav.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * JSON &lt;-&gt; {@link Value} bridge. Uses Jackson streaming
 * ({@code jackson-core}) — no databind.
 *
 * <p>Wire schema (shared with the native cabi side):
 * <ul>
 *   <li>{@code null} ⇄ {@link Value.Null#NULL}</li>
 *   <li>{@code true}/{@code false} ⇄ {@link Value.Bool}</li>
 *   <li>{@code {"$i": "<digits>"}} ⇄ {@link Value.Int}</li>
 *   <li>{@code {"$f": "<text>"}} ⇄ {@link Value.Flt}</li>
 *   <li>string ⇄ {@link Value.Str}</li>
 *   <li>array ⇄ {@link Value.Arr}</li>
 *   <li>object ⇄ {@link Value.Obj} (key order preserved)</li>
 * </ul>
 *
 * <p>Bare JSON numbers (produced neither by cabi nor by {@link #encode})
 * are still accepted on the parse path for defensive round-tripping —
 * integers map to {@link Value.Int}, floats map to {@link Value.Flt}.
 */
public final class WireJson {

    private static final JsonFactory FACTORY = new JsonFactory();

    private WireJson() {
    }

    public static Value decode(byte[] json) {
        try (JsonParser p = FACTORY.createParser(json)) {
            JsonToken t = p.nextToken();
            if (t == null) {
                throw new KtavException("empty JSON from native");
            }
            return readValue(p, t);
        } catch (IOException e) {
            throw new KtavException("decode JSON from native: " + e.getMessage(), e);
        }
    }

    public static byte[] encode(Value v) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        try (JsonGenerator g = FACTORY.createGenerator(out)) {
            writeValue(g, v);
        } catch (IOException e) {
            throw new KtavException("encode JSON for native: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private static Value readValue(JsonParser p, JsonToken t) throws IOException {
        return switch (t) {
            case VALUE_NULL -> Value.Null.NULL;
            case VALUE_TRUE -> Value.Bool.TRUE;
            case VALUE_FALSE -> Value.Bool.FALSE;
            case VALUE_STRING -> new Value.Str(p.getText());
            case VALUE_NUMBER_INT -> new Value.Int(p.getText());
            case VALUE_NUMBER_FLOAT -> new Value.Flt(normaliseFloat(p.getText()));
            case START_ARRAY -> readArray(p);
            case START_OBJECT -> readObject(p);
            default -> throw new KtavException(
                    "unexpected JSON token from native: " + t);
        };
    }

    private static Value readArray(JsonParser p) throws IOException {
        List<Value> items = new ArrayList<>();
        JsonToken t;
        while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
            if (t == null) {
                throw new KtavException("truncated JSON array");
            }
            items.add(readValue(p, t));
        }
        return new Value.Arr(items);
    }

    private static Value readObject(JsonParser p) throws IOException {
        JsonToken t = p.nextToken();
        if (t == JsonToken.END_OBJECT) {
            return new Value.Obj(new LinkedHashMap<>());
        }
        if (t != JsonToken.FIELD_NAME) {
            throw new KtavException("malformed JSON object");
        }
        String k1 = p.currentName();
        JsonToken v1t = p.nextToken();
        Value v1 = readValue(p, v1t);
        JsonToken t2 = p.nextToken();

        if (t2 == JsonToken.END_OBJECT && ("$i".equals(k1) || "$f".equals(k1))) {
            String payload = stringPayload(v1, k1);
            return "$i".equals(k1) ? new Value.Int(payload) : new Value.Flt(payload);
        }

        LinkedHashMap<String, Value> entries = new LinkedHashMap<>();
        entries.put(k1, v1);
        while (t2 != JsonToken.END_OBJECT) {
            if (t2 != JsonToken.FIELD_NAME) {
                throw new KtavException("malformed JSON object");
            }
            String k = p.currentName();
            JsonToken vt = p.nextToken();
            entries.put(k, readValue(p, vt));
            t2 = p.nextToken();
        }
        return new Value.Obj(entries);
    }

    private static String stringPayload(Value v, String key) {
        if (v instanceof Value.Str s) {
            return s.value();
        }
        throw new KtavException(key + " payload must be a string");
    }

    private static String normaliseFloat(String text) {
        if (text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0) {
            return text + ".0";
        }
        return text;
    }

    private static void writeValue(JsonGenerator g, Value v) throws IOException {
        if (v == null || v == Value.Null.NULL) {
            g.writeNull();
            return;
        }
        if (v instanceof Value.Bool b) {
            g.writeBoolean(b.value());
        } else if (v instanceof Value.Int i) {
            g.writeStartObject();
            g.writeStringField("$i", i.text());
            g.writeEndObject();
        } else if (v instanceof Value.Flt f) {
            g.writeStartObject();
            g.writeStringField("$f", f.text());
            g.writeEndObject();
        } else if (v instanceof Value.Str s) {
            g.writeString(s.value());
        } else if (v instanceof Value.Arr a) {
            g.writeStartArray();
            for (Value item : a.items()) {
                writeValue(g, item);
            }
            g.writeEndArray();
        } else if (v instanceof Value.Obj o) {
            g.writeStartObject();
            for (var e : o.entries().entrySet()) {
                g.writeFieldName(e.getKey());
                writeValue(g, e.getValue());
            }
            g.writeEndObject();
        } else {
            throw new KtavException("unexpected Value variant: " + v.getClass());
        }
    }
}
