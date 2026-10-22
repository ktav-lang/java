package lang.ktav;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic algorithmic regression checks for the pure backend:
 * genuine large in-memory inputs (wide, deep, long, repeated) with
 * structural correctness asserted on the outputs — no wall-clock
 * gating, generous fixed sizes that stay CI-safe. If the parser or
 * writer silently degrades to quadratic behavior the CI budget (not a
 * timing assertion) is the safety net; these tests pin CORRECTNESS at
 * sizes where an accidental O(n^2) path also blows the build.
 */
final class PureRegressionTest {

    // ── Wide object: many sibling keys ──────────────────────────────────

    @Test
    void wideObjectWithTenThousandKeys() {
        int n = 10_000;
        StringBuilder src = new StringBuilder();
        for (int i = 0; i < n; i++) {
            src.append("key").append(i).append(": ").append(i).append('\n');
        }
        Value.Obj top = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads(src.toString()));
        assertEquals(n, top.entries().size(), "every key must survive the parse");
        assertEquals(new Value.Int("0"), top.entries().get("key0"));
        assertEquals(new Value.Int(String.valueOf(n - 1)), top.entries().get("key" + (n - 1)));

        // dumps -> loads round trip keeps the whole tree, in order.
        String dumped = lang.ktav.pure.PureKtav.dumps(top);
        Value.Obj back = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads(dumped));
        List<String> keys = new ArrayList<>(back.entries().keySet());
        assertEquals("key0", keys.get(0));
        assertEquals("key" + (n - 1), keys.get(keys.size() - 1));
        assertTrue(CorpusHarness.valueEquals(top, back));
    }

    // ── Deep dotted paths ───────────────────────────────────────────────

    @Test
    void deepDottedPathBuildsTheFullNesting() {
        int depth = 100; // comfortably under PureParser.MAX_DEPTH (256)
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            if (i > 0) path.append('.');
            path.append("level").append(i);
        }
        path.append(": leaf\n");
        Value.Obj top = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads(path.toString()));
        Value cur = top;
        for (int i = 0; i < depth; i++) {
            Value.Obj o = assertInstanceOf(Value.Obj.class, cur,
                    "nesting must reach level " + i);
            cur = o.entries().get("level" + i);
        }
        assertEquals(new Value.Str("leaf"), cur);
    }

    // ── Long strings ────────────────────────────────────────────────────

    @Test
    void longStringSurvivesParseAndCanonicalRoundTrip() {
        String payload = "x".repeat(500_000);
        Value.Obj doc = obj("s", new Value.Str(payload));
        Value parsed = lang.ktav.pure.PureKtav.loads(lang.ktav.pure.PureKtav.dumps(doc));
        assertTrue(CorpusHarness.valueEquals(doc, parsed), "long string round trip");
        String canonical = lang.ktav.pure.PureKtav.emitCanonical(doc);
        assertEquals(payload.length() + "s: ".length() + 1, canonical.length(),
                "canonical output carries the full body exactly once");
    }

    @Test
    void longMultilineStringIsHandledLinearly() {
        String line = "abcdefghij".repeat(50);
        StringBuilder src = new StringBuilder("s: ((\n");
        for (int i = 0; i < 2_000; i++) {
            src.append(line).append('\n');
        }
        src.append("))\n");
        Value.Obj top = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads(src.toString()));
        Value.Str s = assertInstanceOf(Value.Str.class, top.entries().get("s"));
        assertEquals(2_000 * line.length() + 1_999, s.value().length(),
                "verbatim body = lines + separators");
    }

    // ── Deep nested compounds ───────────────────────────────────────────

    @Test
    void deeplyNestedInlineArraysRoundTrip() {
        int depth = 200; // under MAX_DEPTH
        StringBuilder src = new StringBuilder();
        for (int i = 0; i < depth; i++) src.append('[');
        src.append('1');
        for (int i = 0; i < depth; i++) src.append(']');
        Value v = lang.ktav.pure.PureKtav.loads(src.toString());
        for (int i = 0; i < depth; i++) {
            Value.Arr a = assertInstanceOf(Value.Arr.class, v, "nesting must reach " + i);
            assertEquals(1, a.items().size());
            v = a.items().get(0);
        }
        assertEquals(new Value.Int("1"), v);
        // Round trip through the writer keeps the shape.
        Value back = lang.ktav.pure.PureKtav.loads(lang.ktav.pure.PureKtav.dumps(
                lang.ktav.pure.PureKtav.loads(src.toString())));
        assertTrue(CorpusHarness.valueEquals(v, descend(back, depth)));
    }

    private static Value descend(Value v, int depth) {
        for (int i = 0; i < depth; i++) {
            v = assertInstanceOf(Value.Arr.class, v).items().get(0);
        }
        return v;
    }

    // ── Repeated canonicalFromSource: deterministic, allocation-stable ──

    @Test
    void repeatedCanonicalFromSourceIsDeterministic() {
        StringBuilder src = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            src.append("service").append(i).append(".port: ").append(i).append('\n');
            src.append("service").append(i).append(".name: svc\n");
        }
        String first = lang.ktav.pure.PureKtav.canonicalFromSource(src.toString());
        for (int i = 0; i < 500; i++) {
            String again = lang.ktav.pure.PureKtav.canonicalFromSource(src.toString());
            assertEquals(first, again, "iteration " + i + " must be byte-identical");
        }
        // Structural correctness of the canonical output itself.
        Value parsed = lang.ktav.pure.PureKtav.loads(first);
        assertTrue(CorpusHarness.valueEquals(
                lang.ktav.pure.PureKtav.loads(src.toString()), parsed));
        assertEquals(first,
                lang.ktav.pure.PureKtav.canonicalFromSource(first), "fixed point");
    }

    @Test
    void wideDocumentFormatIsAFixedPoint() {
        StringBuilder src = new StringBuilder();
        for (int i = 0; i < 2_000; i++) {
            src.append("k").append(i).append(": v\n");
            if (i % 97 == 0) {
                src.append("## note ").append(i).append('\n');
            }
        }
        String once = lang.ktav.pure.PureKtav.format(src.toString());
        assertEquals(once, lang.ktav.pure.PureKtav.format(once), "format fixed point");
    }

    @Test
    void bigIntegerFallbackStaysStringAtScale() {
        // Many i64-overflow literals in one document: each must fall
        // through to String (§ 5.2 rule 13) with canonical i64 text for
        // the in-range ones.
        StringBuilder src = new StringBuilder();
        String huge = "9".repeat(30);
        for (int i = 0; i < 1_000; i++) {
            src.append("a").append(i).append(": ").append(huge).append('\n');
            src.append("b").append(i).append(": ").append(i).append('\n');
        }
        Value.Obj top = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads(src.toString()));
        assertInstanceOf(Value.Str.class, top.entries().get("a0"));
        Value.Int small = assertInstanceOf(Value.Int.class, top.entries().get("b999"));
        assertEquals(new BigInteger("999"), small.toBigInteger());
    }

    private static Value.Obj obj(String key, Value value) {
        LinkedHashMap<String, Value> m = new LinkedHashMap<>();
        m.put(key, value);
        return new Value.Obj(m);
    }

    @Test
    void multilineArrayItemKeepsItsCommentExactlyOnce() {
        String source = "[\n## keep\n(\nbody\n)\n]\n";
        String formatted = lang.ktav.pure.PureKtav.format(source);
        assertTrue(formatted.contains("## keep"));
        assertEquals(formatted.indexOf("## keep"), formatted.lastIndexOf("## keep"));
        assertEquals(new Value.Arr(List.of(new Value.Str("body"))),
                lang.ktav.pure.PureKtav.loads(formatted));
        assertEquals(formatted, lang.ktav.pure.PureKtav.format(formatted));
    }

    // ── Task7 regressions: nested inline, scope errors, EOF unclosed ────

    @Test
    void nestedInlineObjectsParseExactlyAndRoundTrip() {
        String src = "cfg: {outer: {middle: {inner: deep}}}\n";
        Value expected = obj("cfg", obj("outer",
                obj("middle", obj("inner", new Value.Str("deep")))));
        Value parsed = lang.ktav.pure.PureKtav.loads(src);
        assertTrue(CorpusHarness.valueEquals(expected, parsed),
                "nested inline objects tree: " + parsed);
        String canonical = "cfg: {\n    outer: {\n        middle: {\n"
                + "            inner: deep\n        }\n    }\n}\n";
        assertEquals(canonical, lang.ktav.pure.PureKtav.emitCanonical(parsed));
        assertEquals(canonical, lang.ktav.pure.PureKtav.canonicalFromSource(src));
        assertTrue(CorpusHarness.valueEquals(expected,
                lang.ktav.pure.PureKtav.loads(canonical)));
    }

    @Test
    void arrayOfArraysParsesExactlyAndRoundTrip() {
        String src = "matrix: [[1, 2], [3, 4], [5, 6]]\n";
        Value expected = obj("matrix", new Value.Arr(List.of(
                new Value.Arr(List.of(new Value.Int("1"), new Value.Int("2"))),
                new Value.Arr(List.of(new Value.Int("3"), new Value.Int("4"))),
                new Value.Arr(List.of(new Value.Int("5"), new Value.Int("6"))))));
        Value parsed = lang.ktav.pure.PureKtav.loads(src);
        assertTrue(CorpusHarness.valueEquals(expected, parsed),
                "array-of-arrays tree: " + parsed);
        String canonical = "matrix: [\n    [\n        1\n        2\n    ]\n"
                + "    [\n        3\n        4\n    ]\n"
                + "    [\n        5\n        6\n    ]\n]\n";
        assertEquals(canonical, lang.ktav.pure.PureKtav.emitCanonical(parsed));
        assertEquals(canonical, lang.ktav.pure.PureKtav.canonicalFromSource(src));
        assertTrue(CorpusHarness.valueEquals(expected,
                lang.ktav.pure.PureKtav.loads(canonical)));
    }

    @Test
    void unclosedNonRootArraySpansOpenerToEof() {
        // '[' находится на байте 5, длина исходника — 11 байтов.
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loads("arr: [\n1\n2\n"));
        assertEquals("UnclosedCompound", e.getError());
        assertEquals(5L, e.getSpanStart());
        assertEquals(11L, e.getSpanEnd());
    }

    @Test
    void unclosedNonRootObjectSpansOpenerToEof() {
        // '{' находится на байте 5, длина исходника — 12 байтов.
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loads("obj: {\na: 1\n"));
        assertEquals("UnclosedCompound", e.getError());
        assertEquals(5L, e.getSpanStart());
        assertEquals(12L, e.getSpanEnd());
    }

    @Test
    void unclosedNestedArrayInsideExplicitRootReportsInnermostOpener() {
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loads("[\n[\n1\n"));
        assertEquals("UnclosedCompound", e.getError());
        assertEquals(2L, e.getSpanStart());
        assertEquals(6L, e.getSpanEnd());
    }

    @Test
    void implicitPairRootWithoutClosersStillParses() {
        Value parsed = lang.ktav.pure.PureKtav.loads("a: 1\nb: 2\n");
        LinkedHashMap<String, Value> expected = new LinkedHashMap<>();
        expected.put("a", new Value.Int("1"));
        expected.put("b", new Value.Int("2"));
        assertTrue(CorpusHarness.valueEquals(new Value.Obj(expected), parsed),
                "implicit root must not require a closer");
        Value.Obj top = assertInstanceOf(Value.Obj.class, parsed);
        assertEquals(new Value.Int("1"), top.entries().get("a"));
        assertEquals(new Value.Int("2"), top.entries().get("b"));
    }
}
