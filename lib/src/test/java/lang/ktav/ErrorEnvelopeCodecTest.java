package lang.ktav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import lang.ktav.internal.ErrorEnvelope;

/** Native envelope wire-format regressions; does not invoke the FFI. */
class ErrorEnvelopeCodecTest {
    private static ErrorEnvelope parse(String json) {
        return ErrorEnvelope.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void preservesStructuredFieldsAndSkipsUnknownFields() {
        ErrorEnvelope e = parse("{\"error\":\"LossyScalar\",\"reason\":null,"
                + "\"line\":2,\"line_text\":\"x: 1.10\","
                + "\"span\":{\"start\":5,\"end\":11},\"path\":[\"x\"],"
                + "\"body\":\"1.10\",\"canonical\":\"1.1\","
                + "\"spec_section\":\"§5.2\",\"message\":\"lossy\","
                + "\"future\":{\"ignored\":true}}");
        assertEquals("LossyScalar", e.getError());
        assertNull(e.getReason());
        assertEquals(2L, e.getLine());
        assertEquals("x: 1.10", e.getLineText());
        assertEquals(5L, e.getSpanStart());
        assertEquals(11L, e.getSpanEnd());
        assertEquals(List.of("x"), e.getPath());
        assertEquals("1.10", e.getBody());
        assertEquals("1.1", e.getCanonical());
        assertEquals("§5.2", e.getSpecSection());
        assertEquals("lossy", e.getMessage());
    }

    @Test
    void preservesNullOptionalFields() {
        ErrorEnvelope e = parse("{\"error\":\"Parse\",\"reason\":null,\"line\":null,"
                + "\"line_text\":null,\"span\":null,\"path\":null,\"body\":null,"
                + "\"canonical\":null,\"spec_section\":null,\"message\":\"bad\"}");
        assertEquals("Parse", e.getError());
        assertNull(e.getReason());
        assertNull(e.getLine());
        assertNull(e.getLineText());
        assertNull(e.getSpanStart());
        assertNull(e.getSpanEnd());
        assertNull(e.getPath());
        assertNull(e.getBody());
        assertNull(e.getCanonical());
        assertNull(e.getSpecSection());
        assertEquals("bad", e.getMessage());
    }

    @Test
    void malformedPayloadFallsBackToRawBody() {
        String raw = "not-json";
        ErrorEnvelope e = parse(raw);
        assertEquals("Message", e.getError());
        assertEquals(raw, e.getBody());
        assertNull(e.getMessage());
    }

    @Test
    void malformedStructuredSpanFallsBackToRawBody() {
        String raw = "{\"error\":\"Parse\",\"span\":{\"start\":1,\"end\":\"bad\"}}";
        ErrorEnvelope e = parse(raw);
        assertEquals("Message", e.getError());
        assertEquals(raw, e.getBody());
        assertNull(e.getMessage());
    }

    @Test
    void ofBuildsTheHolderWithoutReconstruction() {
        List<String> path = List.of("a.b");
        ErrorEnvelope e = ErrorEnvelope.of("E", "R", 3L, "line", 4L, 7L,
                path, "body", "canonical", "§1", "message");
        assertEquals("E", e.getError());
        assertEquals("R", e.getReason());
        assertEquals(3L, e.getLine());
        assertEquals("line", e.getLineText());
        assertEquals(4L, e.getSpanStart());
        assertEquals(7L, e.getSpanEnd());
        assertSame(path, e.getPath());
        assertEquals("body", e.getBody());
        assertEquals("canonical", e.getCanonical());
        assertEquals("§1", e.getSpecSection());
        assertEquals("message", e.getMessage());
    }
}
