package lang.ktav;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation tests for the spec § 8.5 manifest guard
 * ({@link CorpusHarness.CorpusManifest#read}): each test writes a
 * KNOWN-GOOD manifest, mutates EXACTLY ONE aspect, and asserts that
 * {@code read} fails with a diagnostic that NAMES the mutation. One
 * control test reads the unmutated manifest to prove the negatives
 * fail for the mutation, not for the base shape.
 */
final class CorpusGuardMutationTest {

    private static final String KNOWN_GOOD = """
            {
              "$comment": "known-good manifest shape (spec § 8.5 / § 6.15)",
              "schema_version": 1,
              "categories": {
                "valid": {"count": 228},
                "invalid": {"count": 90},
                "unrepresentable": {"count": 5},
                "parseable-unrepresentable": {"count": 4},
                "strict-lossy": {"count": 13}
              },
              "fixture_flags": [
                {"category": "invalid", "fixture": "invalid_utf8/f1", "flags": ["raw_bytes"]},
                {"category": "invalid", "fixture": "invalid_utf8/f2", "flags": ["raw_bytes"]},
                {"category": "invalid", "fixture": "invalid_utf8/f3", "flags": ["raw_bytes"]},
                {"category": "invalid", "fixture": "invalid_utf8/f4", "flags": ["raw_bytes"]},
                {"category": "invalid", "fixture": "invalid_utf8/f5", "flags": ["raw_bytes"]},
                {"category": "invalid", "fixture": "invalid_utf8/f6", "flags": ["raw_bytes"]},
                {"category": "invalid", "fixture": "invalid_utf8/f7", "flags": ["raw_bytes"]},
                {"category": "invalid", "fixture": "invalid_utf8/f8", "flags": ["raw_bytes"]},
                {"category": "invalid", "fixture": "invalid_utf8/f9", "flags": ["raw_bytes"]}
              ]
            }
            """;

    @TempDir
    Path temp;

    private Path write(String json) throws IOException {
        Path manifest = temp.resolve("manifest.json");
        Files.write(manifest, json.getBytes(StandardCharsets.UTF_8));
        return manifest;
    }

    /**
     * The KNOWN-GOOD manifest reads WITHOUT throwing — the control
     * proving each mutation test below fails for its mutation only.
     */
    @Test
    void knownGoodManifestReadsCleanly() throws IOException {
        CorpusHarness.CorpusManifest.read(write(KNOWN_GOOD));
    }

    private void assertReadFailsNaming(String json, String... fragments) throws IOException {
        try {
            CorpusHarness.CorpusManifest.read(write(json));
        } catch (AssertionError e) {
            for (String fragment : fragments) {
                assertTrue(String.valueOf(e.getMessage()).contains(fragment),
                        "diagnostic must name the mutation; expected fragment \"" + fragment
                                + "\" in: " + e.getMessage());
            }
            return;
        }
        throw new AssertionError(
                "mutated manifest must be rejected, but CorpusManifest.read accepted it: " + json);
    }

    @Test
    void unimplementedSchemaVersionIsNamed() throws IOException {
        assertReadFailsNaming(KNOWN_GOOD.replace("\"schema_version\": 1",
                "\"schema_version\": 2"), "schema_version");
    }

    @Test
    void missingCategoryIsNamedByClosedSetMessage() throws IOException {
        assertReadFailsNaming(KNOWN_GOOD.replace(
                "    \"invalid\": {\"count\": 90},\n", ""),
                "do not match the closed set", "expected exactly");
    }

    @Test
    void wrongCountIsNamedByClosedSetMessage() throws IOException {
        assertReadFailsNaming(KNOWN_GOOD.replace(
                "\"valid\": {\"count\": 228}", "\"valid\": {\"count\": 227}"),
                "do not match the closed set", "expected exactly");
    }

    @Test
    void extraCategoryIsNamedByClosedSetMessage() throws IOException {
        assertReadFailsNaming(KNOWN_GOOD.replace(
                "    \"valid\": {\"count\": 228},",
                "    \"valid\": {\"count\": 228},\n"
                        + "    \"sneaky\": {\"count\": 3},"),
                "do not match the closed set", "expected exactly");
    }

    @Test
    void unimplementedFlagIsNamed() throws IOException {
        assertReadFailsNaming(KNOWN_GOOD.replace(
                "\"fixture\": \"invalid_utf8/f1\", \"flags\": [\"raw_bytes\"]",
                "\"fixture\": \"invalid_utf8/f1\", \"flags\": [\"raw_bytes_x\"]"),
                "raw_bytes_x", "only raw_bytes");
    }

    @Test
    void wrongRawBytesFlagCountIsNamed() throws IOException {
        assertReadFailsNaming(KNOWN_GOOD.replace(
                "{\"category\": \"invalid\", \"fixture\": \"invalid_utf8/f8\","
                        + " \"flags\": [\"raw_bytes\"]},\n", ""),
                "exactly 9");
    }

    // ── Canonical-companion guard (spec § 5.9.10) ──────────────────────
    //
    // Exercised against a SYNTHETIC valid/ tree via
    // CorpusHarness.checkCanonicalCompanions — the real spec corpus is
    // never read or written here.

    /** Both twins present: the guard passes (control for the mutation). */
    @Test
    void canonicalCompanionsCompletePasses(@TempDir Path temp) throws IOException {
        Path valid = temp.resolve("valid");
        Files.createDirectories(valid);
        writeKtav(valid.resolve("x.ktav"), "a: 1");
        writeKtav(valid.resolve("x.canonical.ktav"), "a: 1");
        CorpusHarness.checkCanonicalCompanions(valid);
    }

    /** A valid/ source WITHOUT its .canonical.ktav twin is named. */
    @Test
    void missingCanonicalCompanionIsNamed(@TempDir Path temp) throws IOException {
        Path valid = temp.resolve("valid");
        Files.createDirectories(valid);
        writeKtav(valid.resolve("x.ktav"), "a: 1");
        writeKtav(valid.resolve("x.canonical.ktav"), "a: 1");
        writeKtav(valid.resolve("y.ktav"), "b: 2"); // the mutation: no twin
        try {
            CorpusHarness.checkCanonicalCompanions(valid);
        } catch (AssertionError e) {
            for (String fragment : new String[] {"y.ktav", "canonical"}) {
                assertTrue(String.valueOf(e.getMessage()).contains(fragment),
                        "diagnostic must name the twinless fixture; expected fragment \""
                                + fragment + "\" in: " + e.getMessage());
            }
            return;
        }
        throw new AssertionError(
                "valid/ source without a .canonical.ktav twin must be rejected");
    }

    private static void writeKtav(Path p, String body) throws IOException {
        Files.write(p, body.getBytes(StandardCharsets.UTF_8));
    }
}
