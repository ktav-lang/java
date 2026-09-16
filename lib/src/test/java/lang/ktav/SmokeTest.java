package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class SmokeTest {

    @BeforeAll
    static void requireNativeLib() {
        TestPaths.init();
        assumeTrue(TestPaths.cabiBuilt(),
                "cabi not built (" + TestPaths.CABI + ") — run `cargo build --release -p ktav-cabi`");
    }

    @Test
    void loadsBasicDocument() {
        // spec 0.5.0: numbers, booleans, and null are inferred from lexical form.
        // No :i / :f typed markers — bare integers and floats are parsed directly.
        String src = """
                service: web
                port: 8080
                ratio: 0.75
                tls: true
                tags: [
                    prod
                    eu-west-1
                ]
                db.host: primary
                db.timeout: 30
                """;
        Value v = Ktav.loads(src);
        Value.Obj top = assertInstanceOf(Value.Obj.class, v);

        assertEquals(new Value.Str("web"), top.entries().get("service"));
        assertEquals(new Value.Int("8080"), top.entries().get("port"));
        assertTrue(top.entries().get("ratio") instanceof Value.Flt);
        assertEquals(Value.Bool.TRUE, top.entries().get("tls"));

        Value.Arr tags = assertInstanceOf(Value.Arr.class, top.entries().get("tags"));
        assertEquals(List.of(new Value.Str("prod"), new Value.Str("eu-west-1")), tags.items());

        Value.Obj db = assertInstanceOf(Value.Obj.class, top.entries().get("db"));
        assertEquals(new Value.Str("primary"), db.entries().get("host"));
        assertEquals(new Value.Int("30"), db.entries().get("timeout"));
    }

    @Test
    void loadsStrictChecksNumericSpelling() {
        assertThrows(KtavException.class, () -> Ktav.loadsStrict("version: 1.10\n"));

        Value v = Ktav.loadsStrict("small: 1e-3\nlarge: 1e10\n");
        Value.Obj top = assertInstanceOf(Value.Obj.class, v);
        assertEquals(new Value.Flt("0.001"), top.entries().get("small"));
        assertEquals(new Value.Flt("10000000000.0"), top.entries().get("large"));
    }

    @Test
    void roundTripSimpleDocument() {
        LinkedHashMap<String, Value> entries = new LinkedHashMap<>();
        entries.put("name", new Value.Str("demo"));
        entries.put("count", Value.Int.of(42));
        entries.put("ratio", Value.Flt.of(0.5));
        entries.put("flag", Value.Bool.TRUE);
        entries.put("nothing", Value.Null.NULL);

        LinkedHashMap<String, Value> nested = new LinkedHashMap<>();
        nested.put("inner", Value.Int.of(1));
        entries.put("nested", new Value.Obj(nested));

        String out = Ktav.dumps(new Value.Obj(entries));
        assertNotNull(out);
        Value back = Ktav.loads(out);
        Value.Obj b = assertInstanceOf(Value.Obj.class, back);
        assertEquals(new Value.Str("demo"), b.entries().get("name"));
        assertEquals(new Value.Int("42"), b.entries().get("count"));
        assertEquals(Value.Bool.TRUE, b.entries().get("flag"));
        assertEquals(Value.Null.NULL, b.entries().get("nothing"));

        Value.Flt ratioBack = assertInstanceOf(Value.Flt.class, b.entries().get("ratio"));
        assertEquals(0.5, ratioBack.toDouble());

        Value.Obj nestedBack = assertInstanceOf(Value.Obj.class, b.entries().get("nested"));
        assertEquals(new Value.Int("1"), nestedBack.entries().get("inner"));
    }

    @Test
    void largeIntegerRoundTrip() {
        // spec 0.5.0: integers within i64 range are parsed as Integer.
        // Values beyond i64 overflow to String (spec § 3.6.2).
        String large = "9999999999";
        Value v = Ktav.loads("value: " + large);
        Value.Obj top = assertInstanceOf(Value.Obj.class, v);
        Value.Int i = assertInstanceOf(Value.Int.class, top.entries().get("value"));
        assertEquals(new BigInteger(large), i.toBigInteger());

        LinkedHashMap<String, Value> m = new LinkedHashMap<>();
        m.put("v", new Value.Int(large));
        String out = Ktav.dumps(new Value.Obj(m));
        assertTrue(out.contains(large), "dump should carry integer literally: " + out);
    }

    @Test
    void parseErrorThrows() {
        assertThrows(KtavException.class, () -> Ktav.loads("a: ["));
    }

    @Test
    void dumpsRejectsScalarTopLevel() {
        // Top-level Array is now valid (spec 0.1.1) — only bare scalars
        // are still rejected by the native side.
        assertThrows(KtavException.class, () -> Ktav.dumps(new Value.Str("just a string")));
    }

    @Test
    void loadsTopLevelArrayBareScalars() {
        // spec 0.1.1: first content line decides Object vs Array.
        String src = """
                alpha
                beta
                gamma
                """;
        Value v = Ktav.loads(src);
        Value.Arr arr = assertInstanceOf(Value.Arr.class, v);
        assertEquals(List.of(
                new Value.Str("alpha"),
                new Value.Str("beta"),
                new Value.Str("gamma")), arr.items());
    }

    @Test
    void loadsTopLevelArrayTypedItems() {
        // spec 0.5.0: bare integers and floats are inferred directly —
        // no :i / :f typed markers needed.
        String src = """
                1
                2
                3.5
                """;
        Value v = Ktav.loads(src);
        Value.Arr arr = assertInstanceOf(Value.Arr.class, v);
        assertEquals(3, arr.items().size());
        assertEquals(new Value.Int("1"), arr.items().get(0));
        assertEquals(new Value.Int("2"), arr.items().get(1));
        Value.Flt third = assertInstanceOf(Value.Flt.class, arr.items().get(2));
        assertEquals(3.5, third.toDouble());
    }

    @Test
    void roundTripTopLevelArray() {
        Value.Arr arr = new Value.Arr(List.of(
                new Value.Str("one"),
                new Value.Str("two"),
                Value.Int.of(3)));
        String out = Ktav.dumps(arr);
        assertNotNull(out);
        Value back = Ktav.loads(out);
        Value.Arr bArr = assertInstanceOf(Value.Arr.class, back);
        assertEquals(3, bArr.items().size());
        assertEquals(new Value.Str("one"), bArr.items().get(0));
        assertEquals(new Value.Str("two"), bArr.items().get(1));
        assertEquals(new Value.Int("3"), bArr.items().get(2));
    }

    @Test
    void toStringForceStringsCoercesScalars() {
        LinkedHashMap<String, Value> entries = new LinkedHashMap<>();
        entries.put("count", Value.Int.of(42));
        entries.put("ratio", Value.Flt.of(0.5));
        entries.put("flag", Value.Bool.TRUE);
        entries.put("nothing", Value.Null.NULL);
        entries.put("name", new Value.Str("demo"));

        String out = Ktav.toStringForceStrings(new Value.Obj(entries));
        assertNotNull(out);

        // Round-trip — every leaf scalar must come back as Value.Str.
        Value back = Ktav.loads(out);
        Value.Obj b = assertInstanceOf(Value.Obj.class, back);
        assertInstanceOf(Value.Str.class, b.entries().get("count"));
        assertInstanceOf(Value.Str.class, b.entries().get("ratio"));
        assertInstanceOf(Value.Str.class, b.entries().get("flag"));
        assertInstanceOf(Value.Str.class, b.entries().get("nothing"));
        assertInstanceOf(Value.Str.class, b.entries().get("name"));

        assertEquals(new Value.Str("42"), b.entries().get("count"));
        assertEquals("true", ((Value.Str) b.entries().get("flag")).value());
        assertEquals("null", ((Value.Str) b.entries().get("nothing")).value());
        assertEquals("demo", ((Value.Str) b.entries().get("name")).value());
    }

    @Test
    void toStringForceStringsAcceptsTopLevelArray() {
        Value.Arr arr = new Value.Arr(List.of(
                Value.Int.of(1),
                Value.Bool.FALSE,
                Value.Null.NULL));
        String out = Ktav.toStringForceStrings(arr);
        assertNotNull(out);
        Value back = Ktav.loads(out);
        Value.Arr bArr = assertInstanceOf(Value.Arr.class, back);
        assertEquals(3, bArr.items().size());
        for (Value item : bArr.items()) {
            assertInstanceOf(Value.Str.class, item);
        }
        assertEquals("1", ((Value.Str) bArr.items().get(0)).value());
        assertEquals("false", ((Value.Str) bArr.items().get(1)).value());
        assertEquals("null", ((Value.Str) bArr.items().get(2)).value());
    }

    @Test
    void loadsQuotedKey() {
        // spec § 5.3.3: quoted key segments. From the 0.7 corpus
        // (valid/quoted_keys/double_quote_basic.*): `"a": 1` parses to
        // {"a": 1}. And per valid/quoted_keys/dot_no_escape_needed.*,
        // a quoted segment is NOT split on dots — `"a.b": 1` yields a
        // single key "a.b", not a nested {a: {b: 1}}.
        Value v = Ktav.loads("\"a.b\": 1\n");
        Value.Obj top = assertInstanceOf(Value.Obj.class, v);
        assertEquals(1, top.entries().size());
        assertEquals(new Value.Int("1"), top.entries().get("a.b"));

        Value v2 = Ktav.loads("\"a\": 1\n");
        Value.Obj top2 = assertInstanceOf(Value.Obj.class, v2);
        assertEquals(new Value.Int("1"), top2.entries().get("a"));
    }

    @Test
    void loadsUnicodeEscapeInInlineValue() {
        // spec § 3.7.1: unicode escape in an inline value. Verified
        // against valid/inline/escape/lowercase_unicode_hex.*: lowercase
        // hex accepted.
        Value v = Ktav.loads("{key: \\" + "u00e9}");
        Value.Obj top = assertInstanceOf(Value.Obj.class, v);
        assertEquals(new Value.Str("é"), top.entries().get("key"));

        // Uppercase hex must be accepted equally (§ 3.7.1 is
        // case-insensitive on the hex digits).
        Value v2 = Ktav.loads("{key: \\" + "u00C9}");
        Value.Obj top2 = assertInstanceOf(Value.Obj.class, v2);
        assertEquals(new Value.Str("É"), top2.entries().get("key"));
    }

    /**
     * A Ktav comment is a line whose first non-whitespace characters are
     * {@code ##} (spec § 3.4). A single {@code #} is ordinary content —
     * there is a corpus fixture named {@code single_hash_is_literal} for
     * exactly this. Writing the test with one {@code #} makes it vacuous:
     * the first line then has no {@code ": "} separator, so § 5.0.1
     * classifies the whole document as an Array root and every line
     * becomes a string item, including {@code service: web}. A
     * "comment survived" assertion would then pass because the text is a
     * VALUE, and would keep passing if the formatter deleted every real
     * comment.
     */
    @Test
    void formatPreservesCommentsAndIsFixedPoint() {
        String src = """
                ## service configuration header
                service: web


                db: {
                    ## nested tuning hint
                    timeout: 30
                }
                """ + "\n\n";

        // The document really is an Object, not an Array of strings.
        assertTrue(Ktav.loads(src) instanceof Value.Obj,
                "test document must be an Object root, or the comment "
                        + "assertions below prove nothing");

        String once = Ktav.format(src);

        assertTrue(once.contains("## service configuration header"),
                "header comment must survive verbatim: " + once);
        assertTrue(once.contains("## nested tuning hint"),
                "comment before a nested key must survive verbatim: " + once);

        // Comments are trivia, not data: they must not appear in the
        // parsed Value at all. This is what separates "preserved by the
        // formatter" from "preserved because it was never a comment".
        assertFalse(Ktav.dumps(Ktav.loads(once)).contains("##"),
                "comments must not survive into the Value model");

        // A run of blank lines collapses to exactly one, and trailing
        // blank padding is dropped — the two rules that make the
        // transform a fixed point.
        assertFalse(once.contains("\n\n\n"),
                "runs of blank lines must collapse to one: " + once);

        assertEquals(once, Ktav.format(once), "format must be a fixed point");
    }

    @Test
    void formatMatchesCanonicalForTriviaFreeDocument() {
        String src = """
                service: web
                port: 8080
                db.host: primary
                """;
        assertEquals(Ktav.emitCanonical(Ktav.loads(src)), Ktav.format(src));
    }

    @Test
    void errorSurfacesEnvelopeFieldsNotJustString() {
        KtavException e = assertThrows(KtavException.class,
                () -> Ktav.loads("a: ["));
        assertEquals("UnclosedCompound", e.getError());
        assertNull(e.getReason());
        assertNotNull(e.getSpanStart());
        assertNotNull(e.getSpanEnd());
        String msg = e.getMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("UnclosedCompound"),
                "message must contain the error class: " + msg);
        assertTrue(!msg.contains("{\"error\""),
                "message must never be a raw JSON blob: " + msg);
    }

    @Test
    void strictErrorCarriesCanonicalAndSpecFields() {
        KtavException e = assertThrows(KtavException.class,
                () -> Ktav.loadsStrict("version: 1.10\n"));
        assertEquals("LossyScalar", e.getError());
        assertEquals(1L, e.getLine());
        assertEquals("version: 1.10", e.getLineText());
        assertEquals("1.10", e.getBody());
        assertEquals("1.1", e.getCanonical());
        assertEquals("§3.6/§5.2", e.getSpecSection());
        assertNull(e.getReason());
    }

    @Test
    void nativeVersionReportsSomething() {
        String v = Ktav.nativeVersion();
        assertNotNull(v);
        assertTrue(!v.isEmpty(), "native version string is empty");
    }
}
