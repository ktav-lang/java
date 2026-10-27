package lang.ktav;

import lang.ktav.pure.PureKtav;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regression contracts for pure removal and key renaming. */
final class PureRemoveRenameTest {
    private static Value i(String n) { return new Value.Int(n); }
    private static Value o(Object... pairs) {
        java.util.LinkedHashMap<String, Value> m = new java.util.LinkedHashMap<>();
        for (int n = 0; n < pairs.length; n += 2) m.put((String) pairs[n], (Value) pairs[n + 1]);
        return new Value.Obj(m);
    }
    private static void both(String text, List<String> segments, boolean rename) {
        String expected = rename ? "a: {z: 1, y: 2}\nb: 3\n" : "a: {y: 2}\nb: 3\n";
        Value tree = rename ? o("a", o("z", i("1"), "y", i("2")), "b", i("3")) : o("a", o("y", i("2")), "b", i("3"));
        String actual = rename ? PureKtav.renameKey(text, "a.x", "z") : PureKtav.removeValue(text, "a.x");
        assertEquals(expected, actual);
        assertEquals(tree, PureKtav.loads(actual));
        String bySegments = rename ? PureKtav.renameKeySegments(text.getBytes(StandardCharsets.UTF_8), segments, "z") : PureKtav.removeValueSegments(text.getBytes(StandardCharsets.UTF_8), segments);
        assertEquals(actual, bySegments);
    }

    @Test void removeExactSemanticsAndOverloads() {
        String src = "a: {x: 1, y: 2}\nb: 3\n";
        both(src, List.of("a", "x"), false);
        assertEquals("b: 2\n", PureKtav.removeValue("a: 1\nb: 2\n".getBytes(StandardCharsets.UTF_8), "a"));
        String eofSecond = "a: 1\nb: 2";
        String eofDeleted = PureKtav.removeValue(eofSecond, "b");
        assertEquals("a: 1", eofDeleted);
        assertEquals(o("a", i("1")), PureKtav.loads(eofDeleted));
        assertEquals(eofDeleted, PureKtav.removeValueSegments(eofSecond, List.of("b")));
        assertEquals(o("a", i("1")), PureKtav.loads(PureKtav.removeValueSegments(eofSecond.getBytes(StandardCharsets.UTF_8), List.of("b"))));
        assertEquals("a: {y: 2}\n", PureKtav.removeValueSegments("a: {x: 1, y: 2}\n", List.of("a", "x")));
        assertEquals("EditNotFound", assertThrows(KtavException.class, () -> PureKtav.removeValue("a: 1\n", "missing")).getError());
        assertEquals("EditNotFound", assertThrows(KtavException.class, () -> PureKtav.removeValueSegments("a: 1\n", List.of())).getError());
        assertEquals("EditInvalidPath", assertThrows(KtavException.class, () -> PureKtav.removeValue("a: 1\n", "a..b")).getError());
        assertEquals("a\\.b: 1\n", PureKtav.removeValueSegments("a\\.b: 1\nx: 2\n", List.of("x")));
    }

    @Test void removePreservesVerbatimDataMarkerInMultilineString() {
        String source = "a: {\n  s: ((\n## data\n))\n}\nb: 2\n";
        String expected = "b: 2\n";
        String byPath = PureKtav.removeValue(source, "a");
        assertEquals(expected, byPath);
        assertEquals(o("b", i("2")), PureKtav.loads(byPath));
        String bySegments = PureKtav.removeValueSegments(source, List.of("a"));
        assertEquals(expected, bySegments);
        assertEquals(o("b", i("2")), PureKtav.loads(bySegments));
        String byPathBytes = PureKtav.removeValue(source.getBytes(StandardCharsets.UTF_8), "a");
        assertEquals(expected, byPathBytes);
        assertEquals(o("b", i("2")), PureKtav.loads(byPathBytes));
        String bySegmentsBytes = PureKtav.removeValueSegments(source.getBytes(StandardCharsets.UTF_8), List.of("a"));
        assertEquals(expected, bySegmentsBytes);
        assertEquals(o("b", i("2")), PureKtav.loads(bySegmentsBytes));
    }

    @Test void renameOrderNoopCollisionAndLiteralSegments() {
        String src = "a: {x: 1, y: 2}\nb: 3\n";
        both(src, List.of("a", "x"), true);
        assertEquals("a: 1\nx: 2\n", PureKtav.renameKey("a: 1\nb: 2\n", "b", "x"));
        String noop = "k:: 042\r\n";
        assertEquals(noop, PureKtav.renameKey(noop.getBytes(StandardCharsets.UTF_8), "k", "k"));
        assertEquals("m: 1\n", PureKtav.renameKeySegments("x\\\\y: 1\n", List.of("x\\y"), "m"));
        KtavException collision = assertThrows(KtavException.class, () -> PureKtav.renameKey("a: 1\nb: 2\n", "a", "b"));
        assertEquals("EditAlreadyExists", collision.getError());
        assertNull(collision.getBody());
        assertEquals("EditNotFound", assertThrows(KtavException.class, () -> PureKtav.renameKeySegments("", List.of("k"), "x")).getError());
        assertEquals("EditInvalidPath", assertThrows(KtavException.class, () -> PureKtav.renameKey("a: 1\n", "a..b", "x")).getError());
        assertEquals("EditUnsupported", assertThrows(KtavException.class, () -> PureKtav.renameKey("items: [1]\n", "items.0", "x")).getError());
        assertEquals("EditUnsupported", assertThrows(KtavException.class, () -> PureKtav.renameKeySegments("items: [1]\n".getBytes(StandardCharsets.UTF_8), List.of("items", "0"), "x")).getError());
    }

    @Test void commentsUnsupportedAndAdjacentCommentsPreserved() {
        String commented = "a: {\n    ## inside a\n    x: 1\n}\nb: 2\n";
        assertEquals("EditUnsupported", assertThrows(KtavException.class, () -> PureKtav.removeValue(commented, "a")).getError());
        String adjacent = "a: {\n    before: 1\n    ## between pairs\n    x: 2\n    ## after target\n    after: 3\n}\n";
        String actual = PureKtav.removeValue(adjacent.getBytes(StandardCharsets.UTF_8), "a.x");
        assertTrue(actual.contains("## between pairs"));
        assertTrue(actual.contains("## after target"));
        assertEquals(o("a", o("before", i("1"), "after", i("3"))), PureKtav.loads(actual));
        String bySegments = PureKtav.removeValueSegments(adjacent, List.of("a", "x"));
        assertEquals(actual, bySegments);
        assertEquals(PureKtav.loads(actual), PureKtav.loads(bySegments));
    }

    @Test void dottedMaterializationKeepsEolEofBomAndArrayParent() {
        String nested = "a: {\n    b: {\n        x: 1\n    }\n}\nz: 2\n";
        String nestedActual = PureKtav.removeValue(nested.getBytes(StandardCharsets.UTF_8), "a.b.x");
        assertEquals(o("a", o("b", o()), "z", i("2")), PureKtav.loads(nestedActual));
        assertEquals(nestedActual, PureKtav.removeValueSegments(nested, List.of("a", "b", "x")));
        String eof = "a: {\n    x: 1\n    last: 2\n}";
        String eofActual = PureKtav.removeValue(eof, "a.last");
        assertEquals(o("a", o("x", i("1"))), PureKtav.loads(eofActual));
        assertEquals(eofActual, PureKtav.removeValueSegments(eof.getBytes(StandardCharsets.UTF_8), List.of("a", "last")));
        String bom = "\uFEFFa: {\n    x: 1\n}\n";
        String bomActual = PureKtav.removeValue(bom.getBytes(StandardCharsets.UTF_8), "a.x");
        assertTrue(bomActual.startsWith("\uFEFF"));
        assertEquals(o("a", o()), PureKtav.loads(bomActual));
        assertEquals(bomActual, PureKtav.removeValueSegments(bom, List.of("a", "x")));
        String array = "a: [\n    {\n        b: {\n            x: 1\n        }\n    }\n]\n";
        String arrayActual = PureKtav.removeValue(array, "a.0.b.x");
        assertEquals(o("a", new Value.Arr(List.of(o("b", o())))), PureKtav.loads(arrayActual));
        assertEquals(arrayActual, PureKtav.removeValueSegments(array.getBytes(StandardCharsets.UTF_8), List.of("a", "0", "b", "x")));
    }

    @Test void dottedMaterializationBlockExactOverloads() {
        String nested = "a: {\n    b.c: 1\n}\n", nestedExpected = "a: {\n    b: {}\n}\n";
        String nestedPath = PureKtav.removeValue(nested, "a.b.c");
        assertEquals(nestedExpected, nestedPath);
        assertEquals(o("a", o("b", o())), PureKtav.loads(nestedPath));
        String nestedSegments = PureKtav.removeValueSegments(nested.getBytes(StandardCharsets.UTF_8), List.of("a", "b", "c"));
        assertEquals(nestedExpected, nestedSegments);
        assertEquals(PureKtav.loads(nestedPath), PureKtav.loads(nestedSegments));
        String array = "a: [\n    {\n        b.c: 1\n    }\n]\n", arrayExpected = "a: [\n    {\n        b: {}\n    }\n]\n";
        String arrayPath = PureKtav.removeValue(array.getBytes(StandardCharsets.UTF_8), "a.0.b.c");
        assertEquals(arrayExpected, arrayPath);
        assertEquals(o("a", new Value.Arr(List.of(o("b", o())))), PureKtav.loads(arrayPath));
        String arraySegments = PureKtav.removeValueSegments(array, List.of("a", "0", "b", "c"));
        assertEquals(arrayExpected, arraySegments);
        assertEquals(PureKtav.loads(arrayPath), PureKtav.loads(arraySegments));
    }

    @Test void dottedMaterializationCrlfBomAndEofExact() {
        String crlf = "a.b.c: 1\r\nz: 2\r\n", crlfExpected = "a.b: {}\r\nz: 2\r\n";
        String crlfPath = PureKtav.removeValue(crlf.getBytes(StandardCharsets.UTF_8), "a.b.c");
        assertEquals(crlfExpected, crlfPath);
        assertEquals(o("a", o("b", o()), "z", i("2")), PureKtav.loads(crlfPath));
        assertEquals(crlfExpected, PureKtav.removeValueSegments(crlf, List.of("a", "b", "c")));
        assertEquals(o("a", o("b", o()), "z", i("2")), PureKtav.loads(PureKtav.removeValueSegments(crlf, List.of("a", "b", "c"))));
        String bom = "\uFEFFa.b.c: 1\n", bomExpected = "\uFEFFa.b: {}\n";
        String bomPath = PureKtav.removeValue(bom, "a.b.c");
        assertEquals(bomExpected, bomPath);
        assertEquals(o("a", o("b", o())), PureKtav.loads(bomPath));
        assertEquals(bomExpected, PureKtav.removeValueSegments(bom.getBytes(StandardCharsets.UTF_8), List.of("a", "b", "c")));
        assertEquals(o("a", o("b", o())), PureKtav.loads(PureKtav.removeValueSegments(bom.getBytes(StandardCharsets.UTF_8), List.of("a", "b", "c"))));
        String eof = "a.b.c: 1", eofExpected = "a.b: {}";
        for (String actual : List.of(
                PureKtav.removeValue(eof, "a.b.c"),
                PureKtav.removeValue(eof.getBytes(StandardCharsets.UTF_8), "a.b.c"),
                PureKtav.removeValueSegments(eof, List.of("a", "b", "c")),
                PureKtav.removeValueSegments(eof.getBytes(StandardCharsets.UTF_8), List.of("a", "b", "c")))) {
            assertEquals(eofExpected, actual);
            assertEquals(o("a", o("b", o())), PureKtav.loads(actual));
        }
    }

    @Test void deepInlineSecondKeyRenamePreservesOrderAndNoop() {
        String src = "outer: {inner: {first: 1, second: 2, third: 3}, tail: 4}\n";
        String expected = "outer: {inner: {first: 1, renamed: 2, third: 3}, tail: 4}\n";
        assertEquals(expected, PureKtav.renameKey(src.getBytes(StandardCharsets.UTF_8), "outer.inner.second", "renamed"));
        assertEquals(expected, PureKtav.renameKeySegments(src, List.of("outer", "inner", "second"), "renamed"));
        assertEquals(o("outer", o("inner", o("first", i("1"), "renamed", i("2"), "third", i("3")), "tail", i("4"))), PureKtav.loads(PureKtav.renameKey(src.getBytes(StandardCharsets.UTF_8), "outer.inner.second", "renamed")));
        assertEquals(src, PureKtav.renameKey(src, "outer.inner.second", "second"));
        assertEquals(src, PureKtav.renameKeySegments(src.getBytes(StandardCharsets.UTF_8), List.of("outer", "inner", "second"), "second"));
    }

    @Test void dottedKeysAreNotPrunedAndSourceWins() {
        String source = "a\\.b: 1\nx: 2\n";
        byte[] malformed = {'x', ':', ' ', (byte) 0xff};
        String expected = source.substring(0, source.indexOf("x: 2"));
        String removed = PureKtav.removeValue(source, "x");
        assertEquals(expected, removed);
        assertEquals(o("a.b", i("1")), PureKtav.loads(removed));
        assertEquals(expected, PureKtav.removeValueSegments(source, List.of("x")));
        String dotted = "a.b: 1\nx: 2\n", dottedRemoved = PureKtav.removeValue(dotted, "a.b");
        assertEquals("a: {}\nx: 2\n", dottedRemoved);
        assertEquals(o("a", o(), "x", i("2")), PureKtav.loads(dottedRemoved));
        assertEquals("InvalidUtf8", assertThrows(KtavException.class, () -> PureKtav.removeValue(malformed, "\\q")).getError());
        assertEquals("InvalidUtf8", assertThrows(KtavException.class, () -> PureKtav.renameKeySegments(malformed, List.of(), "x")).getError());
    }

    @Test void rootInlineRemovalHandlesCommentsAndLexicalEdges() {
        assertRootRemoval("\uFEFF## {\n  {keep: 1, drop: 2} \u2003\n## ] tail\n", "drop",
                "\uFEFF## {\n  {keep: 1} \u2003\n## ] tail\n", o("keep", i("1")));
        assertRootRemoval("\uFEFF \t{keep: 1, drop: 2}\u2003\n## ] object tail\n", "drop",
                "\uFEFF \t{keep: 1}\u2003\n## ] object tail\n", o("keep", i("1")));
        assertRootRemoval("\uFEFF \t[1, 2, 3,]\u2003\n## } array tail\n", "1",
                "\uFEFF \t[1, 3,]\u2003\n## } array tail\n", new Value.Arr(List.of(i("1"), i("3"))));
        assertRootRemoval("[1, 2, 3,]", "1", "[1, 3,]", new Value.Arr(List.of(i("1"), i("3"))));
        assertRootRemoval("\uFEFF## {\n[1, 2, 3,] \u2003\n## } tail\n", "1",
                "\uFEFF## {\n[1, 3,] \u2003\n## } tail\n", new Value.Arr(List.of(i("1"), i("3"))));
        assertRootRemoval("{keep: don't, drop: 2}", "drop", "{keep: don't}", o("keep", new Value.Str("don't")));
        assertRootRemoval("{keep: a\"b, drop: 2}", "drop", "{keep: a\"b}", o("keep", new Value.Str("a\"b")));
        assertRootRemoval("{keep: a[b, drop: 2}", "drop", "{keep: a[b}", o("keep", new Value.Str("a[b")));
        assertRootRemoval("{\"a,b\": 1, \"x.y\": 2, drop: 3,}", "drop",
                "{\"a,b\": 1, \"x.y\": 2,}", o("a,b", i("1"), "x.y", i("2")));
        assertRootRemoval("[a\\,b, x\\,y, drop,]", "2", "[a\\,b, x\\,y,]",
                new Value.Arr(List.of(new Value.Str("a,b"), new Value.Str("x,y"))));
        assertAllRemovalOverloadsThrow("{a.b: 1, a.c: 2}", List.of("a", "b"), "EditUnsupported");
    }

    private static void assertRootRemoval(String source, String path, String expected, Value expectedTree) {
        String byPath = PureKtav.removeValue(source, path);
        String byPathBytes = PureKtav.removeValue(source.getBytes(StandardCharsets.UTF_8), path);
        String bySegments = PureKtav.removeValueSegments(source, List.of(path));
        String bySegmentsBytes = PureKtav.removeValueSegments(source.getBytes(StandardCharsets.UTF_8), List.of(path));
        assertEquals(expected, byPath);
        assertEquals(expected, byPathBytes);
        assertEquals(expected, bySegments);
        assertEquals(expected, bySegmentsBytes);
        assertEquals(expectedTree, PureKtav.loads(byPath));
        assertEquals(expectedTree, PureKtav.loads(byPathBytes));
        assertEquals(expectedTree, PureKtav.loads(bySegments));
        assertEquals(expectedTree, PureKtav.loads(bySegmentsBytes));
    }

    private static void assertAllRemovalOverloadsThrow(String source, List<String> path, String error) {
        assertEquals(error, assertThrows(KtavException.class, () -> PureKtav.removeValue(source, "a.b")).getError());
        assertEquals(error, assertThrows(KtavException.class, () -> PureKtav.removeValue(source.getBytes(StandardCharsets.UTF_8), "a.b")).getError());
        assertEquals(error, assertThrows(KtavException.class, () -> PureKtav.removeValueSegments(source, path)).getError());
        assertEquals(error, assertThrows(KtavException.class, () -> PureKtav.removeValueSegments(source.getBytes(StandardCharsets.UTF_8), path)).getError());
    }
}
