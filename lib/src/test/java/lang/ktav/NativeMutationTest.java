package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Native-only regression port of Rust CABI mutation wrapper cases. */
final class NativeMutationTest {
    @BeforeAll
    static void requireBuiltCabi() {
        TestPaths.init();
        String debugFlag = System.getenv("KTAV_DEBUG_CABI");
        if (debugFlag != null && debugFlag.matches("(?i)debug")) {
            String libPath = System.getenv("KTAV_LIB_PATH");
            assertTrue(libPath != null && Files.isRegularFile(Paths.get(libPath)),
                    CorpusHarness.CABI_MISSING + " (KTAV_LIB_PATH=" + libPath + ")");
        } else {
            assertTrue(TestPaths.cabiBuilt(), CorpusHarness.CABI_MISSING);
        }
    }

    private static Value integer(String n) { return new Value.Int(n); }
    private static Value string(String s) { return new Value.Str(s); }

    @Test
    void insertTextualSegmentsAndBoundaries() {
        assertEquals("a: 1\nb: 9007199254740993\n",
                Ktav.insertValue("a: 1\n", "b", integer("9007199254740993")));
        assertEquals("a: 1\nb: -0.0\n",
                Ktav.insertValueSegments("a: 1\n", List.of("b"), new Value.Flt("-0.0")));
        assertEquals("a: {x: 1, y: two}\n",
                Ktav.insertValue("a: {x: 1}\n", "a.y", string("two")));
        assertEquals("a: 1\n\"a.b\": 2\n",
                Ktav.insertValue("a: 1\n", "a\\.b", integer("2")));
        assertEquals("## header\nk: v\n", Ktav.insertValue("## header\n", "k", string("v")));
        assertEquals("a: 1\r\nb: v\r\n", Ktav.insertValue("a: 1\r\n", "b", string("v")));
        KtavException collision = assertThrows(KtavException.class,
                () -> Ktav.insertValue("a: 1\nb: 2\n", "b", Value.Null.NULL));
        assertEquals("EditAlreadyExists", collision.getError());
        assertNull(collision.getBody());
        assertEquals("EditNotFound", assertThrows(KtavException.class,
                () -> Ktav.insertValue("a: 1\n", "b.c", Value.Null.NULL)).getError());
        KtavException invalid = assertThrows(KtavException.class,
                () -> Ktav.insertValue("a: 1\n", "a..b", Value.Null.NULL));
        assertEquals("EditInvalidPath", invalid.getError());
        assertEquals("2", invalid.getBody());
    }

    @Test
    void removeTextualSegmentsAndArrayBoundaries() {
        assertEquals("b: 2\n", Ktav.removeValue("a: 1\nb: 2\n", "a"));
        assertEquals("a: [1, 3]\n",
                Ktav.removeValueSegments("a: [1, 2, 3]\n", List.of("a", "1")));
        assertEquals("EditNotFound", assertThrows(KtavException.class,
                () -> Ktav.removeValue("b: 1\n", "a")).getError());
        assertEquals("EditNotFound", assertThrows(KtavException.class,
                () -> Ktav.removeValueSegments("a: 1\n", List.of())).getError());

    }

    @Test
    void renameTextualSegmentsNoopAndCollisions() {
        assertEquals("a: 1\nx: 2\n", Ktav.renameKey("a: 1\nb: 2\n", "b", "x"));
        assertEquals("a: {w: 1, y: 2}\n",
                Ktav.renameKeySegments("a: {x: 1, y: 2}\n", List.of("a", "x"), "w"));
        assertEquals("k:: 042\r\n", Ktav.renameKey("k:: 042\r\n", "k", "k"));
        KtavException collision = assertThrows(KtavException.class,
                () -> Ktav.renameKey("a: 1\nb: 2\n", "a", "b"));
        assertEquals("EditAlreadyExists", collision.getError());
        assertNull(collision.getBody());
        assertEquals("ключ: 1\n", Ktav.renameKey("a: 1\n", "a", "ключ"));
        assertEquals("k: 1\nx\\\\y: 2\n", Ktav.insertValue("k: 1\n", "x\\\\y", integer("2")));
        assertEquals("m: 1\n", Ktav.renameKeySegments("x\\\\y: 1\n", List.of("x\\y"), "m"));
    }

    @Test
    void sourceFirstForMalformedUtf8AndInvalidPathAcrossOperations() {
        byte[] invalidUtf8 = new byte[] {'a', ':', ' ', (byte) 0xff};
        assertSourceFirst(() -> Ktav.insertValue(invalidUtf8, "\\q", Value.Null.NULL));
        assertSourceFirst(() -> Ktav.insertValueSegments(invalidUtf8, List.of(), Value.Null.NULL));
        assertSourceFirst(() -> Ktav.removeValue(invalidUtf8, "\\q"));
        assertSourceFirst(() -> Ktav.removeValueSegments(invalidUtf8, List.of()));
        assertSourceFirst(() -> Ktav.renameKey(invalidUtf8, "\\q", "x"));
        assertSourceFirst(() -> Ktav.renameKeySegments(invalidUtf8, List.of(), "x"));
    }

    private interface Call { void run(); }

    private static void assertSourceFirst(Call call) {
        KtavException e = assertThrows(KtavException.class, call::run);
        assertEquals("InvalidUtf8", e.getError());
        assertEquals(Long.valueOf(1), e.getLine());
        assertEquals(Long.valueOf(3), e.getSpanStart());
        assertEquals(Long.valueOf(4), e.getSpanEnd());
    }

    @Test
    void sourceOffsetIsUtf8ByteBasedAndMutationErrorsHaveExactBodies() {
        KtavException invalid = assertThrows(KtavException.class,
                () -> Ktav.insertValue("я: 1\n", "я..x", Value.Null.NULL));
        assertEquals("EditInvalidPath", invalid.getError());
        assertEquals("3", invalid.getBody());
        assertNull(invalid.getLine());
        KtavException collision = assertThrows(KtavException.class,
                () -> Ktav.renameKey("a: 1\nb: 2\n", "a", "b"));
        assertEquals("EditAlreadyExists", collision.getError());
        assertNull(collision.getBody());
    }

    @Test
    void losslessCommentsBomCrlfAndEmptySource() {
        assertEquals("a: 1\n## tail\nb: v\n",
                Ktav.insertValue("a: 1\n## tail\n", "b", string("v")));
        String bomCrlf = "\uFEFFk:: +1\r\n";
        assertEquals(bomCrlf, Ktav.renameKey(bomCrlf, "k", "k"));
        assertEquals("k: v", Ktav.insertValue("", "k", string("v")));
        assertEquals("k: v", Ktav.insertValueSegments("", List.of("k"), string("v")));
        assertEquals("EditNotFound", assertThrows(KtavException.class,
                () -> Ktav.removeValue("", "k")).getError());
        assertEquals("EditNotFound", assertThrows(KtavException.class,
                () -> Ktav.removeValueSegments("", List.of("k"))).getError());
        assertEquals("EditNotFound", assertThrows(KtavException.class,
                () -> Ktav.renameKey("", "k", "x")).getError());
        assertEquals("EditNotFound", assertThrows(KtavException.class,
                () -> Ktav.renameKeySegments("", List.of("k"), "x")).getError());
    }
}
