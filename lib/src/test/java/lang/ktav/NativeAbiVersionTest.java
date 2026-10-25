package lang.ktav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ABI gate: a loaded native library must report ABI version 2 — the 0.9
 * exported set (with the source-edit symbols). An ABI-1 binary must be
 * rejected with a clear version-mismatch error, never silently used and
 * never hidden behind a pure fallback. Runs only where the cabi exists;
 * the rejection path itself is exercised by the loader, so this test
 * pins the positive side and the expected constant.
 */
final class NativeAbiVersionTest {

    @Test
    void loadedLibraryReportsAbi2() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                TestPaths.cabiBuilt(),
                CorpusHarness.CABI_MISSING + " (" + TestPaths.CABI + ")");
        TestPaths.init();
        // The loader itself enforces the version; reaching the call
        // proves the gate accepted the library. The value is re-read
        // here so a stale EXPECTED_ABI_VERSION constant fails loudly.
        int abi = lang.ktav.internal.NativeLib.get().ktav_abi_version();
        assertEquals(2, abi,
                "ktav_cabi must report ABI 2 for 0.9 (source-edit exports)");
        assertTrue(Ktav.nativeVersion() != null);
    }
}
