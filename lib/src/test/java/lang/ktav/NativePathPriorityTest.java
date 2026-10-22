package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native-backend counterpart of the § 5.3.2 path-priority negative
 * tests ({@code PureNegativeTest}): the blocked scalar prefix wins over
 * a later bad escape, with the FULL raw-split path on the KeyPathConflict
 * envelope, across all four operations. Runs ONLY where the cabi is
 * built (same guard as {@link NativeByteInputTest}).
 */
class NativePathPriorityTest {

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

    private static final String CONFLICT = "a: 1\na.\"\\q\": 2\n";
    private static final String CONTROL = "a: {}\na.\"\\q\": 2\n";

    @Test
    void conflictVectorKeyPathConflictWithPathOnAllFourOperations() {
        assertConflict("loads", () -> Ktav.loads(CONFLICT));
        assertConflict("loadsStrict", () -> Ktav.loadsStrict(CONFLICT));
        assertConflict("format", () -> Ktav.format(CONFLICT));
        assertConflict("canonicalFromSource",
                () -> Ktav.canonicalFromSource(CONFLICT));
    }

    private interface Op {
        void run();
    }

    private static void assertConflict(String op, Op body) {
        KtavException e = assertThrows(KtavException.class, body::run, op);
        assertEquals("KeyPathConflict", e.getError(), op);
        assertEquals("§6.3", e.getSpecSection(), op);
        assertEquals(List.of("a", "\"\\q\""), e.getPath(), op);
    }

    @Test
    void controlVectorBadEscapeSequenceOnAllFourOperations() {
        assertBadEscape("loads", () -> Ktav.loads(CONTROL));
        assertBadEscape("loadsStrict", () -> Ktav.loadsStrict(CONTROL));
        assertBadEscape("format", () -> Ktav.format(CONTROL));
        assertBadEscape("canonicalFromSource",
                () -> Ktav.canonicalFromSource(CONTROL));
    }

    private static void assertBadEscape(String op, Op body) {
        KtavException e = assertThrows(KtavException.class, body::run, op);
        assertEquals("BadEscapeSequence", e.getError(), op);
        assertEquals("§6.13", e.getSpecSection(), op);
    }
}
