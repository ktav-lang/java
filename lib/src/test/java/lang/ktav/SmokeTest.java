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
    void dumpsRejectsNonObjectTopLevel() {
        Value arr = new Value.Arr(List.of(new Value.Str("x")));
        assertThrows(KtavException.class, () -> Ktav.dumps(arr));
    }

    @Test
    void nativeVersionReportsSomething() {
        String v = Ktav.nativeVersion();
        assertNotNull(v);
        assertTrue(!v.isEmpty(), "native version string is empty");
    }
}
