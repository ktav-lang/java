package lang.ktav;

import lang.ktav.pure.PureKtav;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Independent source-first mutation contracts for the pure backend. */
final class PureMutationSourceFirstReuseTest {
    private static Value i(String n) { return new Value.Int(n); }
    private static Value s(String n) { return new Value.Str(n); }
    private static Value o(Object... pairs) {
        LinkedHashMap<String, Value> m = new LinkedHashMap<>();
        for (int n = 0; n < pairs.length; n += 2) m.put((String) pairs[n], (Value) pairs[n + 1]);
        return new Value.Obj(m);
    }
    private static void same(String expected, Value tree, String... actuals) {
        for (String actual : actuals) {
            assertEquals(expected, actual);
            assertEquals(tree, PureKtav.loads(actual));
        }
    }
    @Test void allMutationEntryPointsHaveExactIndependentResultsAndPreserveOrder() {
        String source = "\uFEFFparent: {first: café, target: 2, last: 3}\r\ntail: 4\r\n";
        String inserted = "\uFEFFparent: {first: café, target: 2, last: 3, added: oui}\r\ntail: 4\r\n";
        Value insertedTree = o("parent", o("first", s("café"), "target", i("2"), "last", i("3"), "added", s("oui")), "tail", i("4"));
        same(inserted, insertedTree,
                PureKtav.insertValue(source, "parent.added", s("oui")),
                PureKtav.insertValue(source.getBytes(StandardCharsets.UTF_8), "parent.added", s("oui")),
                PureKtav.insertValueSegments(source, List.of("parent", "added"), s("oui")),
                PureKtav.insertValueSegments(source.getBytes(StandardCharsets.UTF_8), List.of("parent", "added"), s("oui")));

        String removed = "\uFEFFparent: {first: café, last: 3}\r\ntail: 4\r\n";
        Value removedTree = o("parent", o("first", s("café"), "last", i("3")), "tail", i("4"));
        same(removed, removedTree,
                PureKtav.removeValue(source, "parent.target"),
                PureKtav.removeValue(source.getBytes(StandardCharsets.UTF_8), "parent.target"),
                PureKtav.removeValueSegments(source, List.of("parent", "target")),
                PureKtav.removeValueSegments(source.getBytes(StandardCharsets.UTF_8), List.of("parent", "target")));

        String renamed = "\uFEFFparent: {first: café, renamed: 2, last: 3}\r\ntail: 4\r\n";
        Value renamedTree = o("parent", o("first", s("café"), "renamed", i("2"), "last", i("3")), "tail", i("4"));
        same(renamed, renamedTree,
                PureKtav.renameKey(source, "parent.target", "renamed"),
                PureKtav.renameKey(source.getBytes(StandardCharsets.UTF_8), "parent.target", "renamed"),
                PureKtav.renameKeySegments(source, List.of("parent", "target"), "renamed"),
                PureKtav.renameKeySegments(source.getBytes(StandardCharsets.UTF_8), List.of("parent", "target"), "renamed"));
    }

    @Test void malformedSourceWinsOverPathParentAndEditPayloadFailures() {
        String malformed = "a: [";
        byte[] bytes = malformed.getBytes(StandardCharsets.UTF_8);
        assertEquals("UnclosedCompound", assertThrows(KtavException.class,
                () -> PureKtav.insertValue(malformed, "a..b", i("1"))).getError());
        assertEquals("UnclosedCompound", assertThrows(KtavException.class,
                () -> PureKtav.removeValue(malformed, "a..b")).getError());
        assertEquals("UnclosedCompound", assertThrows(KtavException.class,
                () -> PureKtav.renameKey(malformed, "a..b", "")).getError());
        assertEquals("UnclosedCompound", assertThrows(KtavException.class,
                () -> PureKtav.insertValue(bytes, "a..b", i("1"))).getError());
        assertEquals("UnclosedCompound", assertThrows(KtavException.class,
                () -> PureKtav.removeValueSegments(bytes, List.of("missing", "child"))).getError());
        assertEquals("UnclosedCompound", assertThrows(KtavException.class,
                () -> PureKtav.insertValueSegments(malformed, List.of("missing", ""), i("1"))).getError());
        assertEquals("UnclosedCompound", assertThrows(KtavException.class,
                () -> PureKtav.renameKeySegments(malformed, List.of("missing"), "")).getError());
    }

    @Test void malformedUtf8WinsOverBadPathAndEditArguments() {
        byte[] malformed = {'a', ':', ' ', (byte) 0xff};
        assertEquals("InvalidUtf8", assertThrows(KtavException.class,
                () -> PureKtav.insertValue(malformed, "\\q", i("1"))).getError());
        assertEquals("InvalidUtf8", assertThrows(KtavException.class,
                () -> PureKtav.removeValue(malformed, "\\q")).getError());
        assertEquals("InvalidUtf8", assertThrows(KtavException.class,
                () -> PureKtav.renameKey(malformed, "\\q", "")).getError());
    }
}
