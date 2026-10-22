package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native-backend conformance: all five corpus categories via the shared
 * {@link CorpusHarness} with the native {@link Ktav} facade (the same
 * runner {@code PureConformanceTest} drives with the pure backend), plus
 * the binding-side {@code WriterPrecheck} and manifest-schema checks.
 * The harness carries every corpus assertion — nothing is weaker here
 * than before the harness existed.
 */
final class ConformanceTest {

    static {
        TestPaths.init();
    }

    private static final Backend NATIVE = Backend.nativeBackend();

    // ── Spec § 8.2: unrepresentable values surface NORMATIVE reasons ────
    //
    // Both of these conditions are invisible to the native writers (the
    // JSON wire rejects them first, as opaque Message-class errors with
    // reason == null), so the binding itself raises them — see
    // WriterPrecheck. The reason codes are what the spec mandates, so
    // they are what we assert, exactly.

    @Test
    void scalarRootSurfacesNormativeReason() {
        KtavException e = assertThrows(KtavException.class, () -> Ktav.dumps(new Value.Str("x")));
        assertEquals("UnrepresentableAt", e.getError());
        assertEquals("ScalarRoot", e.getReason());
        assertNotNull(e.getPath(), "path must be present, and empty");
        assertTrue(e.getPath().isEmpty(),
                "ScalarRoot path is empty — the offense is the root itself");
        assertEquals("§5.9.0", e.getSpecSection());
        assertTrue(e.getMessage().startsWith(
                        "ScalarRoot: the document root is not an Object or an Array"),
                "message: " + e.getMessage());
    }

    @Test
    void nonFiniteFloatSurfacesNormativeReasonWithPath() {
        LinkedHashMap<String, Value> inner = new LinkedHashMap<>();
        inner.put("b", new Value.Flt("NaN"));
        LinkedHashMap<String, Value> outer = new LinkedHashMap<>();
        outer.put("a", new Value.Obj(inner));

        KtavException e = assertThrows(KtavException.class,
                () -> Ktav.dumps(new Value.Obj(outer)));
        assertEquals("UnrepresentableAt", e.getError());
        assertEquals("NonFiniteFloat", e.getReason());
        assertEquals(List.of("a", "b"), e.getPath());
        assertEquals("§5.9.0", e.getSpecSection());
        assertTrue(e.getMessage().contains("NonFiniteFloat"), "message: " + e.getMessage());
        assertTrue(e.getMessage().contains("at [\"a\", \"b\"]"), "message: " + e.getMessage());
    }

    // ── Corpus layout: spec § 8.5 manifest compliance ───────────────────

    /**
     * Validates the complete corpus before any factory enumerates
     * fixtures (spec &sect; 8.5).
     */
    @BeforeAll
    static void validateCorpusBeforeFixtures() throws IOException {
        assertTrue(TestPaths.cabiBuilt(), CorpusHarness.CABI_MISSING);
        assertTrue(TestPaths.specPresent(), CorpusHarness.SPEC_MISSING);
        CorpusHarness.runManifestGuard();
    }

    /**
     * Any schema version this runner does not implement must fail — 0 and
     * negatives included, not just newer ones (spec § 8.5: refuse to
     * guess at an unrecognised shape). Uses a temp manifest; the spec
     * submodule's own fixtures are never edited.
     */
    @Test
    void manifestReaderRejectsAnySchemaVersionOtherThanOne(@TempDir Path dir)
            throws IOException {
        Path manifest = dir.resolve("manifest.json");
        Files.writeString(manifest,
                "{\"schema_version\":0,\"categories\":{\"valid\":{\"count\":1}}}");
        assertThrows(AssertionFailedError.class,
                () -> CorpusHarness.CorpusManifest.read(manifest),
                "schema_version 0 is unimplemented and must fail");
        Files.writeString(manifest,
                "{\"schema_version\":-2,\"categories\":{\"valid\":{\"count\":1}}}");
        assertThrows(AssertionFailedError.class,
                () -> CorpusHarness.CorpusManifest.read(manifest),
                "negative schema versions are unimplemented and must fail");
        Files.writeString(manifest,
                "{\"schema_version\":2,\"categories\":{\"valid\":{\"count\":1}}}");
        assertThrows(AssertionFailedError.class,
                () -> CorpusHarness.CorpusManifest.read(manifest),
                "schema_version 2 is unimplemented and must fail");
    }

    @TestFactory
    Stream<DynamicTest> validFixtures() throws IOException {
        return CorpusHarness.validFixtures(NATIVE);
    }

    @TestFactory
    Stream<DynamicTest> canonicalFixtures() throws IOException {
        return CorpusHarness.canonicalFixtures(NATIVE);
    }

    @TestFactory
    Stream<DynamicTest> unrepresentableFixtures() throws IOException {
        return CorpusHarness.unrepresentableFixtures(NATIVE);
    }

    @TestFactory
    Stream<DynamicTest> parseableUnrepresentableFixtures() throws IOException {
        return CorpusHarness.parseableUnrepresentableFixtures(NATIVE);
    }

    @TestFactory
    Stream<DynamicTest> strictLossyFixtures() throws IOException {
        return CorpusHarness.strictLossyFixtures(NATIVE);
    }

    @TestFactory
    Stream<DynamicTest> invalidFixtures() throws IOException {
        return CorpusHarness.invalidFixtures(NATIVE);
    }
}
