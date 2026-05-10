package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        String src = """
                service: web
                port:i 8080
                ratio:f 0.75
                tls: true
                tags: [
                    prod
                    eu-west-1
                ]
                db.host: primary
                db.timeout:i 30
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
    void arbitraryPrecisionIntegerRoundTrip() {
        String huge = "99999999999999999999";
        Value v = Ktav.loads("value:i " + huge);
        Value.Obj top = (Value.Obj) v;
        Value.Int i = assertInstanceOf(Value.Int.class, top.entries().get("value"));
        assertEquals(new BigInteger(huge), i.toBigInteger());

        LinkedHashMap<String, Value> m = new LinkedHashMap<>();
        m.put("v", new Value.Int(huge));
        String out = Ktav.dumps(new Value.Obj(m));
        assertTrue(out.contains(huge), "dump should carry big integer literally: " + out);
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
        String src = """
                :i 1
                :i 2
                :f 3.5
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
    void nativeVersionReportsSomething() {
        String v = Ktav.nativeVersion();
        assertNotNull(v);
        assertTrue(!v.isEmpty(), "native version string is empty");
    }
}
