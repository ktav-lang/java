package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Empty-input ABI invariant: the C ABI dereferences the source pointer as
 * a Rust slice header, which must be non-null and aligned even for length
 * 0. These calls used to pass {@code Pointer.NULL} for empty input — a
 * debug cabi aborts there if the invariant regresses.
 */
class NativeEmptyInputTest {

    @BeforeAll
    static void requireBuiltCabiAndSpec() {
        TestPaths.init();
        String debugFlag = System.getenv("KTAV_DEBUG_CABI");
        if (debugFlag != null && debugFlag.matches("(?i)debug")) {
            // The debug CI job pins the cabi via KTAV_LIB_PATH; the
            // default TestPaths.CABI path (target/release) does not exist.
            String libPath = System.getenv("KTAV_LIB_PATH");
            assertTrue(libPath != null && Files.isRegularFile(Paths.get(libPath)),
                    CorpusHarness.CABI_MISSING + " (KTAV_LIB_PATH=" + libPath + ")");
        } else {
            assertTrue(TestPaths.cabiBuilt(), CorpusHarness.CABI_MISSING);
        }
        assertTrue(TestPaths.specPresent(), CorpusHarness.SPEC_MISSING);
    }

    @Test
    void loadsEmptySourceYieldsEmptyObject() {
        Value.Obj obj = (Value.Obj) Ktav.loads("");
        assertTrue(obj.entries().isEmpty(), "loads(\"\") must yield zero entries, got " + obj.entries());
    }

    @Test
    void loadsStrictEmptySourceYieldsEmptyObject() {
        Value.Obj obj = (Value.Obj) Ktav.loadsStrict("");
        assertTrue(obj.entries().isEmpty(), "loadsStrict(\"\") must yield zero entries, got " + obj.entries());
    }

    @Test
    void formatEmptySourceYieldsEmptyString() {
        assertEquals("", Ktav.format(""), "format(\"\") must return zero bytes");
    }

    @Test
    void canonicalFromSourceEmptySourceYieldsEmptyString() {
        assertEquals("", Ktav.canonicalFromSource(""), "canonicalFromSource(\"\") must return zero bytes");
    }

    @Test
    void emitCanonicalOfLoadedEmptySourceYieldsEmptyString() {
        assertEquals("", Ktav.emitCanonical(Ktav.loads("")),
                "emitCanonical(loads(\"\")) must return zero bytes");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KTAV_DEBUG_CABI", matches = "(?i)debug")
    void debugArtifactIsActive() {
        String libPath = System.getenv("KTAV_LIB_PATH");
        assertTrue(libPath != null && !libPath.isEmpty(),
                "KTAV_LIB_PATH must be set when KTAV_DEBUG_CABI=debug");
        assertTrue(Files.isRegularFile(Paths.get(libPath)),
                "KTAV_LIB_PATH does not point at a regular file: " + libPath);
        assertFalse(libPath.replace('\\', '/').contains("/release/"),
                "resolved native library must be the DEBUG artifact, not a release fallback: " + libPath);
    }
}
