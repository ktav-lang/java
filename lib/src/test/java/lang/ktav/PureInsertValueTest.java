package lang.ktav;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure source insertion regressions with independent semantic and byte-exact oracles. */
final class PureInsertValueTest {
    private static Value.Int i(String n) { return new Value.Int(n); }
    private static Value.Str s(String v) { return new Value.Str(v); }
    private static Value.Obj o(Object... pairs) {
        java.util.LinkedHashMap<String, Value> m = new java.util.LinkedHashMap<>();
        for (int n = 0; n < pairs.length; n += 2) m.put((String) pairs[n], (Value) pairs[n + 1]);
        return new Value.Obj(m);
    }
    private static void exactAndTree(String output, String expectedBytes, Value expectedTree) {
        assertEquals(expectedBytes, output);
        assertEquals(expectedTree, lang.ktav.pure.PureKtav.loads(output));
    }

    @Test void rootInlineBlockAndTrailingComma() {
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("a: 1\n", "b", i("2")), "a: 1\nb: 2\n", o("a", i("1"), "b", i("2")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("cfg: {x: 1,}\n", "cfg.y", i("2")), "cfg: {x: 1, y: 2,}\n", o("cfg", o("x", i("1"), "y", i("2"))));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("plain: {x: 1}\n", "plain.y", i("2")), "plain: {x: 1, y: 2}\n", o("plain", o("x", i("1"), "y", i("2"))));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("cfg: {\n    x: 1\n}\n", "cfg.y", i("2")), "cfg: {\n    x: 1\n    y: 2\n}\n", o("cfg", o("x", i("1"), "y", i("2"))));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("items: [1, 2,]\n", "added", new Value.Bool(true)), "items: [1, 2,]\nadded: true\n", o("items", new Value.Arr(List.of(i("1"), i("2"))), "added", new Value.Bool(true)));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("items: [{x: 1}]\n", "items.0.y", i("2")), "items: [{x: 1, y: 2}]\n", o("items", new Value.Arr(List.of(o("x", i("1"), "y", i("2"))))));
    }

    @Test void escapedDelimitersAndSegments() {
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("a: 1\n", "a\\.b", i("3")), "a: 1\n\"a.b\": 3\n", o("a", i("1"), "a.b", i("3")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments("a: 1\n", List.of("a.b"), i("3")), "a: 1\n\"a.b\": 3\n", o("a", i("1"), "a.b", i("3")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments("parent: {}\na: 1\n", List.of("parent", "c,d"), s("v")), "parent: {\"c,d\": v}\na: 1\n", o("parent", o("c,d", s("v")), "a", i("1")));
    }

    @Test void stringAndByteSourcesAndI64() {
        String source = "n: 9223372036854775807\n";
        Value expected = o("n", i("9223372036854775807"), "huge", s("9223372036854775808"));
        String bytes = source + "huge:: 9223372036854775808\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "huge", s("9223372036854775808")), bytes, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("huge"), s("9223372036854775808")), bytes, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source.getBytes(StandardCharsets.UTF_8), "huge", s("9223372036854775808")), bytes, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source.getBytes(StandardCharsets.UTF_8), List.of("huge"), s("9223372036854775808")), bytes, expected);
        assertEquals(new BigInteger("9223372036854775807"), ((Value.Int) ((Value.Obj) lang.ktav.pure.PureKtav.loads(source)).entries().get("n")).toBigInteger());
    }

    @Test void insertionIntoWhitespaceEmptyInlineObjects() {
        String source = "parent: { \u2003 }\n";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        Value expected = o("parent", o("child", i("2")));
        String output = "parent: {child: 2}\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.child", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "child"), i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.child", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "child"), i("2")), output, expected);
        Value strTree = o("parent", o("child", s("v")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.child", s("v")), "parent: {child: v}\n", strTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "child"), s("v")), "parent: {child: v}\n", strTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.child", s("v")), "parent: {child: v}\n", strTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "child"), s("v")), "parent: {child: v}\n", strTree);
    }

    @Test void insertionIntoWhitespaceEmptyArrayItemObject() {
        String source = "items: [\n    { \u2003 }\n]\n";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        Value expected = o("items", new Value.Arr(List.of(o("child", i("2")))));
        String output = "items: [\n    {child: 2}\n]\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "items.0.child", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("items", "0", "child"), i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "items.0.child", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("items", "0", "child"), i("2")), output, expected);
    }

    @Test void nonemptyStandalonePairAndBlockArrayItemFourOverloads() {
        String source = "{a: 1}\n", output = "{a: 1, b: 2}\n";
        Value expected = o("a", i("1"), "b", i("2"));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("b"), i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source.getBytes(StandardCharsets.UTF_8), "b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source.getBytes(StandardCharsets.UTF_8), List.of("b"), i("2")), output, expected);

        String item = "items: [\n    {x: 1}\n]\n", itemOut = "items: [\n    {x: 1, y: two}\n]\n";
        byte[] itemBytes = item.getBytes(StandardCharsets.UTF_8);
        Value itemTree = o("items", new Value.Arr(List.of(o("x", i("1"), "y", s("two")))));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(item, "items.0.y", s("two")), itemOut, itemTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(item, List.of("items", "0", "y"), s("two")), itemOut, itemTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(itemBytes, "items.0.y", s("two")), itemOut, itemTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(itemBytes, List.of("items", "0", "y"), s("two")), itemOut, itemTree);
    }

    @Test void nestedBlockItemLongAndMultilineFourOverloads() {
        String source = "items: [\n    {x: 1}\n]\n";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        String longValue = "x".repeat(120);
        Value longTree = o("items", new Value.Arr(List.of(o("x", i("1"), "y", s(longValue)))));
        String longOutput = "items: [\n    {\n        x: 1\n        y: " + longValue + "\n    }\n]\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "items.0.y", s(longValue)), longOutput, longTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("items", "0", "y"), s(longValue)), longOutput, longTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "items.0.y", s(longValue)), longOutput, longTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("items", "0", "y"), s(longValue)), longOutput, longTree);
        String multiline = "first\nsecond";
        Value multilineTree = o("items", new Value.Arr(List.of(o("x", i("1"), "y", s(multiline)))));
        String multilineOutput = "items: [\n    {\n        x: 1\n        y: ((\nfirst\nsecond\n        ))\n    }\n]\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "items.0.y", s(multiline)), multilineOutput, multilineTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("items", "0", "y"), s(multiline)), multilineOutput, multilineTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "items.0.y", s(multiline)), multilineOutput, multilineTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("items", "0", "y"), s(multiline)), multilineOutput, multilineTree);
    }

    @Test void nestedInlinePairOrdinaryLongAndMultiline() {
        String source = "parent: {x: one}\n";
        String inline = "parent: {x: one, y: two}\n";
        Value inlineTree = o("parent", o("x", s("one"), "y", s("two")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", s("two")), inline, inlineTree);
        String longValue = "x".repeat(120);
        String longOutput = "parent: {\n    x: one\n    y: " + longValue + "\n}\n";
        Value longTree = o("parent", o("x", s("one"), "y", s(longValue)));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", s(longValue)), longOutput, longTree);
        String multiline = "first\nsecond";
        String multilineOutput = "parent: {\n    x: one\n    y: ((\nfirst\nsecond\n    ))\n}\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", s(multiline)), multilineOutput,
                o("parent", o("x", s("one"), "y", s(multiline))));
    }

    @Test void rootFormattingBomAndMixedEol() {
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("a: 1\r\nb: 2\n", "c", i("3")), "a: 1\r\nb: 2\nc: 3\n", o("a", i("1"), "b", i("2"), "c", i("3")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("\uFEFF", "k", i("1")), "\uFEFF\nk: 1", o("k", i("1")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("\uFEFF\r\n", "k", i("1")), "\uFEFF\r\nk: 1\r\n", o("k", i("1")));
        String mixed = "parent: {\r\n    x: 1\n}\r\ntail: keep\n";
        String mixedOutput = "parent: {\r\n    x: 1\n    y: 2\r\n}\r\ntail: keep\n";
        Value mixedTree = o("parent", o("x", i("1"), "y", i("2")), "tail", s("keep"));
        byte[] mixedBytes = mixed.getBytes(StandardCharsets.UTF_8);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(mixed, "parent.y", i("2")), mixedOutput, mixedTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(mixed, List.of("parent", "y"), i("2")), mixedOutput, mixedTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(mixedBytes, "parent.y", i("2")), mixedOutput, mixedTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(mixedBytes, List.of("parent", "y"), i("2")), mixedOutput, mixedTree);
        String crlf = "parent: {\r\n    x: 1\r\n}\r\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(crlf, "parent.y", i("2")), "parent: {\r\n    x: 1\r\n    y: 2\r\n}\r\n", o("parent", o("x", i("1"), "y", i("2"))));
    }

    @Test void nestedVerbatimClosingMarkerFourOverloadsAndCrFallback() {
        String source = "parent: {x: 1}\n";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        String marker = "first\nlast";
        String output = "parent: {\n    x: 1\n    marker: ((\nfirst\nlast\n    ))\n}\n";
        Value tree = o("parent", o("x", i("1"), "marker", s(marker)));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.marker", s(marker)), output, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "marker"), s(marker)), output, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.marker", s(marker)), output, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "marker"), s(marker)), output, tree);
        String crlfSource = "parent: {\r\n    x: 1\r\n}\r\n";
        byte[] crlfBytes = crlfSource.getBytes(StandardCharsets.UTF_8);
        Value crlfTree = o("parent", o("x", i("1"), "y", o("z", i("2"))));
        String crlfOutput = "parent: {\r\n    x: 1\r\n    y: {\r\n        z: 2\r\n    }\r\n}\r\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(crlfSource, "parent.y", o("z", i("2"))), crlfOutput, crlfTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(crlfSource, List.of("parent", "y"), o("z", i("2"))), crlfOutput, crlfTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(crlfBytes, "parent.y", o("z", i("2"))), crlfOutput, crlfTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(crlfBytes, List.of("parent", "y"), o("z", i("2"))), crlfOutput, crlfTree);
    }

    @Test void restoredEscapedCommaBackslashCases() {
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("parent: {x: p\\,}\n", "parent.q", s("v")),
                "parent: {x: p\\,, q: v}\n", o("parent", o("x", s("p,"), "q", s("v"))));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("parent: {}\n", "parent.a\\\\b", s("v")),
                "parent: {a\\\\b: v}\n", o("parent", o("a\\b", s("v"))));
    }

    @Test void nestedObjectInsertionOverloads() {
        String source = "parent: {x: 1}\n";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        Value value = o("z", i("2"));
        Value tree = o("parent", o("x", i("1"), "y", value));
        String output = "parent: {x: 1, y: {z: 2}}\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", value), output, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "y"), value), output, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.y", value), output, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "y"), value), output, tree);
    }


    @Test void standaloneBlockLfEmptyContainersCrlfAndRootEofPolicyFourOverloads() {
        String source = "base: 1\n";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        java.util.function.BiConsumer<Value, String> rootCase = (value, output) -> {
            Value expected = o("base", i("1"), "k", value);
            exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "k", value), output, expected);
            exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("k"), value), output, expected);
            exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "k", value), output, expected);
            exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("k"), value), output, expected);
        };
        rootCase.accept(s("one\ntwo"), "base: 1\nk: ((\none\ntwo\n))\n");
        rootCase.accept(o(), "base: 1\nk: {\n}\n");
        rootCase.accept(new Value.Arr(List.of()), "base: 1\nk: [\n]\n");

        String nested = "parent: {\r\n    x: 1\r\n}\r\n";
        byte[] nestedBytes = nested.getBytes(StandardCharsets.UTF_8);
        java.util.function.BiConsumer<Value, String> nestedCase = (value, body) -> {
            Value expected = o("parent", o("x", i("1"), "y", value));
            String output = "parent: {\r\n    x: 1\r\n    y: " + body + "\r\n}\r\n";
            exactAndTree(lang.ktav.pure.PureKtav.insertValue(nested, "parent.y", value), output, expected);
            exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(nested, List.of("parent", "y"), value), output, expected);
            exactAndTree(lang.ktav.pure.PureKtav.insertValue(nestedBytes, "parent.y", value), output, expected);
            exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(nestedBytes, List.of("parent", "y"), value), output, expected);
        };
        nestedCase.accept(s("one\ntwo"), "((\r\none\r\ntwo\r\n    ))");
        nestedCase.accept(o(), "{\r\n    }" );
        nestedCase.accept(new Value.Arr(List.of()), "[\r\n    ]");

        String noFinalEol = "base: 1";
        byte[] noFinalEolBytes = noFinalEol.getBytes(StandardCharsets.UTF_8);
        String noFinalEolOutput = "base: 1\nk: ((\none\ntwo\n))";
        Value noFinalEolTree = o("base", i("1"), "k", s("one\ntwo"));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(noFinalEol, "k", s("one\ntwo")), noFinalEolOutput, noFinalEolTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(noFinalEol, List.of("k"), s("one\ntwo")), noFinalEolOutput, noFinalEolTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(noFinalEolBytes, "k", s("one\ntwo")), noFinalEolOutput, noFinalEolTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(noFinalEolBytes, List.of("k"), s("one\ntwo")), noFinalEolOutput, noFinalEolTree);

        KtavException cr1 = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValue(source, "k", s("x\ry")));
        KtavException cr2 = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("k"), s("x\ry")));
        KtavException cr3 = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValue(bytes, "k", s("x\ry")));
        KtavException cr4 = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("k"), s("x\ry")));
        assertEquals("EditUnsupported", cr1.getError());
        assertEquals("EditUnsupported", cr2.getError());
        assertEquals("EditUnsupported", cr3.getError());
        assertEquals("EditUnsupported", cr4.getError());
    }
    @Test void malformedUtf8PrecedesInvalidPathAndStandaloneCrIsUnsupported() {
        byte[] malformed = {'a', ':', ' ', (byte) 0xff};
        KtavException invalidUtf8 = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.insertValue(malformed, "\\q", i("1")));
        assertEquals("InvalidUtf8", invalidUtf8.getError());
        KtavException cr = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.insertValue("a: 1\n", "cr", s("x\ry")));
        assertEquals("EditUnsupported", cr.getError());
    }

    @Test void stringsLoopRootEmptyNonemptyAndBlock() {
        String[] values = {"123", "true", "null", "a,b", "a\\b", "line\nfeed"};
        for (String value : values) {
            String root = lang.ktav.pure.PureKtav.insertValue("", "value", s(value));
            assertEquals(o("value", s(value)), lang.ktav.pure.PureKtav.loads(root));

            String empty = lang.ktav.pure.PureKtav.insertValue("parent: {}\n", "parent.child", s(value));
            assertEquals(o("parent", o("child", s(value))), lang.ktav.pure.PureKtav.loads(empty));

            String nonempty = lang.ktav.pure.PureKtav.insertValue("parent: {x: 0}\n", "parent.y", s(value));
            assertEquals(o("parent", o("x", i("0"), "y", s(value))), lang.ktav.pure.PureKtav.loads(nonempty));

            String block = lang.ktav.pure.PureKtav.insertValue("parent: {\n    x: 0\n}\n", "parent.y", s(value));
            assertEquals(o("parent", o("x", i("0"), "y", s(value))), lang.ktav.pure.PureKtav.loads(block));
        }
    }

    @Test void pairAllValueFormsFourOverloads() {
        String source = "parent: {x: one}\n";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        String ordinary = "parent: {x: one, y: two}\n";
        Value ordinaryTree = o("parent", o("x", s("one"), "y", s("two")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", s("two")), ordinary, ordinaryTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "y"), s("two")), ordinary, ordinaryTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.y", s("two")), ordinary, ordinaryTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "y"), s("two")), ordinary, ordinaryTree);

        String longValue = "x".repeat(120);
        String longOutput = "parent: {\n    x: one\n    y: " + longValue + "\n}\n";
        Value longTree = o("parent", o("x", s("one"), "y", s(longValue)));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", s(longValue)), longOutput, longTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "y"), s(longValue)), longOutput, longTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.y", s(longValue)), longOutput, longTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "y"), s(longValue)), longOutput, longTree);

        String multiline = "first\nsecond";
        String multilineOutput = "parent: {\n    x: one\n    y: ((\nfirst\nsecond\n    ))\n}\n";
        Value multilineTree = o("parent", o("x", s("one"), "y", s(multiline)));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", s(multiline)), multilineOutput, multilineTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "y"), s(multiline)), multilineOutput, multilineTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.y", s(multiline)), multilineOutput, multilineTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "y"), s(multiline)), multilineOutput, multilineTree);

        String bom = "\uFEFF";
        String bomOut = "\uFEFF\nk: 1";
        Value bomTree = o("k", i("1"));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bom, "k", i("1")), bomOut, bomTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bom, List.of("k"), i("1")), bomOut, bomTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bom.getBytes(StandardCharsets.UTF_8), "k", i("1")), bomOut, bomTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bom.getBytes(StandardCharsets.UTF_8), List.of("k"), i("1")), bomOut, bomTree);

        String cr = "first\ry";
        String crOut = "parent: {x: one, y: first\\ry}\n";
        Value crTree = o("parent", o("x", s("one"), "y", s(cr)));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", s(cr)), crOut, crTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "y"), s(cr)), crOut, crTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.y", s(cr)), crOut, crTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "y"), s(cr)), crOut, crTree);

        String markerOut = "parent: {x: one, y: first\\n))\\nlast}\n";
        Value markerTree = o("parent", o("x", s("one"), "y", s("first\n))\nlast")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.y", s("first\n))\nlast")), markerOut, markerTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "y"), s("first\n))\nlast")), markerOut, markerTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.y", s("first\n))\nlast")), markerOut, markerTree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "y"), s("first\n))\nlast")), markerOut, markerTree);
    }

    @Test void leadingCommentsAndLiteralRootStringsRemainCovered() {
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("## note\n{x: 1}\n", "y", i("2")),
                "## note\n{x: 1, y: 2}\n", o("x", i("1"), "y", i("2")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("", "k", s("x\\,y")),
                "k: x\\,y", o("k", s("x\\,y")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValue("", "k", s("a,b")),
                "k: a,b", o("k", s("a,b")));
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments("base: 0\n", List.of("a,b"), i("1")),
                "base: 0\n\"a,b\": 1\n", o("base", i("0"), "a,b", i("1")));
    }

    @Test void blockInsertionReindentsVerbatimCloserWithoutChangingData() {
        String source = "parent: {\r\n    keep: 1\r\n}\r\n";
        Value value = o("value", s("first\n)\nlast"));
        String expected = "parent: {\r\n    keep: 1\r\n    added: {\r\n        value: ((\r\nfirst\r\n)\r\nlast\r\n        ))\r\n    }\r\n}\r\n";
        Value tree = o("parent", o("keep", i("1"), "added", value));
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.added", value), expected, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "added"), value), expected, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.added", value), expected, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "added"), value), expected, tree);
    }

    @Test void inlineRootLayoutWithBomAndSpaces() {
        String source = "﻿  {x: 1}\r\n";
        String expected = "﻿  {\r\n    x: 1\r\n    y: ((\r\nfirst\r\nsecond\r\n    ))\r\n}\r\n";
        Value value = s("first\nsecond");
        Value tree = o("x", i("1"), "y", value);
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "y", value), expected, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("y"), value), expected, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "y", value), expected, tree);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("y"), value), expected, tree);
    }

    @Test void insertionErrorsAreStable() {
        KtavException emptyFinal = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.insertValueSegments("a: 1\n", List.of(""), i("2")));
        assertEquals("EditUnsupported", emptyFinal.getError());
        KtavException missingBeforeEmpty = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.insertValueSegments("a: 1\n", List.of("missing", ""), i("2")));
        assertEquals("EditNotFound", missingBeforeEmpty.getError());
        KtavException noncontiguous = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValue("a.x: 1\na.y: 2\n", "a.z", i("3")));
        assertEquals("EditUnsupported", noncontiguous.getError());
        KtavException arrayParent = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValue("items: [1, 2]\n", "items.child", i("3")));
        assertEquals("EditUnsupported", arrayParent.getError());
        KtavException missingParent = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValueSegments("a: 1\n", List.of("missing", "child"), i("2")));
        assertEquals("EditNotFound", missingParent.getError());
        KtavException badPath = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValue("a: 1\n", "a..b", i("2")));
        assertEquals("EditInvalidPath", badPath.getError());
        assertEquals("2", badPath.getBody());
        KtavException exists = assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.insertValueSegments("a: 1\n", List.of("a"), i("2")));
        assertEquals("EditAlreadyExists", exists.getError());
    }

    @Test void standaloneAndInlineEscapedKeysAllFourOverloads() {
        String source = "base: 1\n"; byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        Value expected = o("base", i("1"), "a b", i("2"));
        String output = "base: 1\na b: 2\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "a b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("a b"), i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "a b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("a b"), i("2")), output, expected);

        Value edgeExpected = o("base", i("1"), "k", s(" value "));
        String edgeOutput = "base: 1\nk: ((\n value \n))\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "k", s(" value ")), edgeOutput, edgeExpected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("k"), s(" value ")), edgeOutput, edgeExpected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "k", s(" value ")), edgeOutput, edgeExpected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("k"), s(" value ")), edgeOutput, edgeExpected);

        source = "parent: {x: 1}\n"; bytes = source.getBytes(StandardCharsets.UTF_8);
        expected = o("parent", o("x", i("1"), "a b", i("2")));
        output = "parent: {x: 1, a b: 2}\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.a b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "a b"), i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.a b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "a b"), i("2")), output, expected);

        source = "base: 1\n"; bytes = source.getBytes(StandardCharsets.UTF_8);
        expected = o("base", i("1"), "a\\b", i("2"));
        output = "base: 1\na\\\\b: 2\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "a\\\\b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("a\\b"), i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "a\\\\b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("a\\b"), i("2")), output, expected);

        source = "parent: {x: 1}\n"; bytes = source.getBytes(StandardCharsets.UTF_8);
        expected = o("parent", o("x", i("1"), "a\\b", i("2")));
        output = "parent: {x: 1, a\\\\b: 2}\n";
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(source, "parent.a\\\\b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(source, List.of("parent", "a\\b"), i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValue(bytes, "parent.a\\\\b", i("2")), output, expected);
        exactAndTree(lang.ktav.pure.PureKtav.insertValueSegments(bytes, List.of("parent", "a\\b"), i("2")), output, expected);
    }

    @Test void emptyFinalKeyAndSourceValidationOrder() {
        for (boolean standalone : List.of(false, true)) {
            String source = standalone ? "a: 1\n" : "parent: {x: 1}\n";
            byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
            List<String> segments = standalone ? List.of("") : List.of("parent", "");
            for (boolean compound : List.of(false, true)) {
                Value value = compound ? o("x", i("2")) : i("2");
                KtavException stringError = assertThrows(KtavException.class,
                        () -> lang.ktav.pure.PureKtav.insertValueSegments(source, segments, value));
                assertEquals("EditUnsupported", stringError.getError());
                assertEquals("empty key name is not representable", stringError.getBody());
                KtavException byteError = assertThrows(KtavException.class,
                        () -> lang.ktav.pure.PureKtav.insertValueSegments(bytes, segments, value));
                assertEquals("EditUnsupported", byteError.getError());
                assertEquals("empty key name is not representable", byteError.getBody());
            }
        }
        KtavException invalidSource = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.insertValueSegments("a: [\n", List.of(""), i("2")));
        assertEquals("UnclosedCompound", invalidSource.getError());
    }
}
