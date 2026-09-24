package lang.ktav;

import lang.ktav.internal.WireJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import org.opentest4j.AssertionFailedError;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class ConformanceTest {

    private static final String CABI_MISSING =
            "ktav_cabi is not built — run `cargo build --release -p ktav-cabi`";
    private static final String SPEC_MISSING =
            "spec submodule is missing — run `git submodule update --init spec`";

    static {
        TestPaths.init();
    }

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
     * Validates the corpus against {@code tests/manifest.json} (spec
     * &sect; 8.5): the manifest is the normative inventory of which
     * category directories MUST exist, with how many fixtures each. This
     * replaces the hand-rolled whitelist this runner used to carry — a
     * stale or truncated corpus (a partially checked-out submodule, an
     * old spec version's tests/) still has every category present and
     * non-empty, so only the EXACT counts catch that we are running
     * fewer cases than the corpus defines.
     *
     * <p>One dynamic test, not a passing skip: a runner whose corpus is
     * unusable must say so loudly rather than report green.
     */
    @TestFactory
    Stream<DynamicTest> manifest() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(failTest("manifest: cabi not built", CABI_MISSING));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(failTest("manifest: spec submodule missing", SPEC_MISSING));
        }
        return Stream.of(DynamicTest.dynamicTest("manifest", ConformanceTest::runManifestGuard));
    }

    /**
     * Any schema version this runner does not implement must fail — 0 and
     * negatives included, not just newer ones (spec § 8.5: refuse to
     * guess at an unrecognised shape). Regression: 0 used to slip through
     * the "newer than implemented" check. Uses a temp manifest; the spec
     * submodule's own fixtures are never edited.
     */
    @Test
    void manifestReaderRejectsAnySchemaVersionOtherThanOne(@TempDir Path dir)
            throws IOException {
        Path manifest = dir.resolve("manifest.json");
        Files.writeString(manifest,
                "{\"schema_version\":0,\"categories\":{\"valid\":{\"count\":1}}}");
        assertThrows(AssertionFailedError.class, () -> CorpusManifest.read(manifest),
                "schema_version 0 is unimplemented and must fail");
        Files.writeString(manifest,
                "{\"schema_version\":-2,\"categories\":{\"valid\":{\"count\":1}}}");
        assertThrows(AssertionFailedError.class, () -> CorpusManifest.read(manifest),
                "negative schema versions are unimplemented and must fail");
        Files.writeString(manifest,
                "{\"schema_version\":2,\"categories\":{\"valid\":{\"count\":1}}}");
        assertThrows(AssertionFailedError.class, () -> CorpusManifest.read(manifest),
                "schema_version 2 is unimplemented and must fail");
    }

    private static void runManifestGuard() throws IOException {
        CorpusManifest manifest = CorpusManifest.read(TestPaths.SPEC.resolve("manifest.json"));

        // The manifest's category list is CLOSED: a directory that
        // appears without being declared (or without its count being
        // updated in the same change) is a corpus the runner does not
        // understand — it must not be silently ignored.
        Set<String> foundDirs;
        try (Stream<Path> dirs = Files.list(TestPaths.SPEC)) {
            foundDirs = dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        for (String name : foundDirs) {
            assertTrue(manifest.categories().containsKey(name),
                    "unexpected directory under tests/: " + name
                            + " — not a category declared by manifest.json (spec § 8.5), so the"
                            + " runner would silently ignore it");
        }
        for (String category : manifest.categories().keySet()) {
            assertTrue(Files.isDirectory(TestPaths.SPEC.resolve(category)),
                    "manifest declares category \"" + category + "\" but tests/" + category
                            + "/ is absent");
        }

        for (var e : manifest.categories().entrySet()) {
            String category = e.getKey();
            int expected = e.getValue();
            assertTrue(expected > 0, "manifest declares " + expected + " fixtures for " + category);
            int actual = countFixtures(category);
            assertTrue(actual == expected,
                    "category " + category + " holds " + actual
                            + " fixture(s) but manifest.json declares " + expected
                            + " (spec § 8.5) — the corpus and its manifest must move together");
        }
    }

    /**
     * Fixture counting rule, from the manifest's own {@code $comment}: a
     * fixture is one {@code <name>} stem shared by the category's
     * sibling files — a {@code .ktav}/{@code .json}/{@code .canonical.ktav}
     * triple under {@code valid/}, a {@code .ktav}/{@code .json} pair
     * under {@code invalid/}, {@code parseable-unrepresentable/} or
     * {@code strict-lossy/}, or a single {@code .json} under {@code
     * unrepresentable/}. Stems are counted per directory, since "sibling"
     * is what shares them.
     */
    private static int countFixtures(String category) throws IOException {
        Path root = TestPaths.SPEC.resolve(category);
        if ("unrepresentable".equals(category)) {
            return collectJsonFiles(root).size();
        }
        Set<String> stems = new LinkedHashSet<>();
        for (Path p : collectKtavFiles(root)) {
            String rel = root.relativize(p).toString().replace('\\', '/');
            stems.add(rel.substring(0, rel.length() - ".ktav".length()));
        }
        return stems.size();
    }

    /**
     * Spec &sect; 8.5 corpus manifest: {@code schema_version} (this
     * runner implements 1), the closed {@code categories} map with each
     * category's EXACT fixture count, and {@code fixture_flags} — the
     * per-fixture handling notes, of which only {@code raw_bytes} (spec
     * &sect; 6.15, .ktav input deliberately not valid UTF-8) is defined
     * today. Parsed with Jackson streaming, like
     * {@link lang.ktav.internal.ErrorEnvelope}; the manifest is small and
     * its shape is normative, so a malformed one fails rather than
     * degrades.
     */
    private static final class CorpusManifest {

        private static final JsonFactory FACTORY = new JsonFactory();

        private final Map<String, Integer> categories;
        private final Set<String> rawByteFixtures;

        private CorpusManifest(Map<String, Integer> categories, Set<String> rawByteFixtures) {
            this.categories = categories;
            this.rawByteFixtures = rawByteFixtures;
        }

        /** Category name -> exact number of fixtures it must contain. */
        Map<String, Integer> categories() {
            return categories;
        }

        /** {@code "category/fixture"} stems flagged {@code raw_bytes}. */
        Set<String> rawByteFixtures() {
            return rawByteFixtures;
        }

        static CorpusManifest read(Path path) throws IOException {
            if (!Files.isRegularFile(path)) {
                fail("manifest.json is missing at " + path
                        + " — the normative corpus inventory (spec § 8.5) comes with the spec submodule");
            }
            long schemaVersion = -1;
            Map<String, Integer> categories = new LinkedHashMap<>();
            Set<String> rawBytes = new LinkedHashSet<>();
            try (JsonParser p = FACTORY.createParser(Files.readAllBytes(path))) {
                if (p.nextToken() != JsonToken.START_OBJECT) {
                    fail("manifest.json is not a JSON object: " + path);
                }
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
                    if (t != JsonToken.FIELD_NAME) {
                        fail("malformed manifest.json (expected a field name): " + path);
                    }
                    switch (p.currentName()) {
                        case "schema_version" ->
                                schemaVersion = readInt(p, "schema_version", path);
                        case "categories" -> readCategories(p, categories, path);
                        case "fixture_flags" -> readFixtureFlags(p, rawBytes, path);
                        default -> {
                            // Forward compatibility, same as the error
                            // envelope: skip what a newer manifest adds
                            // instead of refusing to read it.
                            p.nextToken();
                            p.skipChildren();
                        }
                    }
                }
            }
            if (schemaVersion == -1) {
                fail("manifest.json has no schema_version: " + path);
            }
            if (schemaVersion != 1) {
                fail("manifest.json schema_version " + schemaVersion
                        + " is not implemented by this runner (1) — refusing to guess at an"
                        + " unrecognised shape");
            }
            if (categories.isEmpty()) {
                fail("manifest.json declares no categories: " + path);
            }
            return new CorpusManifest(categories, rawBytes);
        }

        private static long readInt(JsonParser p, String field, Path path) throws IOException {
            if (p.nextToken() != JsonToken.VALUE_NUMBER_INT) {
                fail("manifest.json field \"" + field + "\" must be an integer: " + path);
            }
            return p.getLongValue();
        }

        private static String readString(JsonParser p, String field, Path path)
                throws IOException {
            if (p.nextToken() != JsonToken.VALUE_STRING) {
                fail("manifest.json field \"" + field + "\" must be a string: " + path);
            }
            return p.getText();
        }

        private static void readCategories(
                JsonParser p, Map<String, Integer> out, Path path) throws IOException {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                fail("manifest.json \"categories\" must be an object: " + path);
            }
            JsonToken t;
            while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
                if (t != JsonToken.FIELD_NAME) {
                    fail("malformed manifest.json \"categories\" object: " + path);
                }
                String name = p.currentName();
                if (p.nextToken() != JsonToken.START_OBJECT) {
                    fail("manifest.json category \"" + name + "\" must be an object: " + path);
                }
                long count = -1;
                JsonToken ft;
                while ((ft = p.nextToken()) != JsonToken.END_OBJECT) {
                    if (ft != JsonToken.FIELD_NAME) {
                        fail("malformed manifest.json category \"" + name + "\": " + path);
                    }
                    if ("count".equals(p.currentName())) {
                        count = readInt(p, "categories." + name + ".count", path);
                    } else {
                        p.nextToken();
                        p.skipChildren();
                    }
                }
                if (count < 0) {
                    fail("manifest.json category \"" + name + "\" has no count: " + path);
                }
                out.put(name, (int) count);
            }
        }

        private static void readFixtureFlags(
                JsonParser p, Set<String> rawByteStems, Path path) throws IOException {
            if (p.nextToken() != JsonToken.START_ARRAY) {
                fail("manifest.json \"fixture_flags\" must be an array: " + path);
            }
            JsonToken t = p.nextToken();
            while (t != JsonToken.END_ARRAY) {
                if (t != JsonToken.START_OBJECT) {
                    fail("malformed manifest.json \"fixture_flags\" entry: " + path);
                }
                String category = null;
                String fixture = null;
                Set<String> flags = Set.of();
                JsonToken ft;
                while ((ft = p.nextToken()) != JsonToken.END_OBJECT) {
                    if (ft != JsonToken.FIELD_NAME) {
                        fail("malformed manifest.json \"fixture_flags\" entry: " + path);
                    }
                    switch (p.currentName()) {
                        case "category" -> category = readString(p, "category", path);
                        case "fixture" -> fixture = readString(p, "fixture", path);
                        case "flags" -> flags = readFlagList(p, path);
                        default -> {
                            p.nextToken();
                            p.skipChildren();
                        }
                    }
                }
                if (category == null || fixture == null) {
                    fail("manifest.json fixture_flags entry needs a category and a fixture: "
                            + path);
                }
                if (flags.contains("raw_bytes")) {
                    rawByteStems.add(category + "/" + fixture);
                }
                t = p.nextToken();
            }
        }

        private static Set<String> readFlagList(JsonParser p, Path path) throws IOException {
            if (p.nextToken() != JsonToken.START_ARRAY) {
                fail("manifest.json fixture_flags \"flags\" must be an array: " + path);
            }
            Set<String> flags = new LinkedHashSet<>();
            JsonToken t = p.nextToken();
            while (t != JsonToken.END_ARRAY) {
                if (t != JsonToken.VALUE_STRING) {
                    fail("manifest.json fixture_flags \"flags\" must be strings: " + path);
                }
                flags.add(p.getText());
                t = p.nextToken();
            }
            return flags;
        }
    }

    @TestFactory
    Stream<DynamicTest> validFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(failTest("valid: cabi not built", CABI_MISSING));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(failTest("valid: spec submodule missing", SPEC_MISSING));
        }
        Path root = TestPaths.SPEC.resolve("valid");
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        relativeName(root, p),
                        () -> runValid(p)));
    }

    private void runValid(Path ktavPath) throws IOException {
        Path oraclePath = ktavPath.resolveSibling(
                ktavPath.getFileName().toString().replaceFirst("\\.ktav$", ".json"));
        byte[] src = Files.readAllBytes(ktavPath);
        byte[] oracle = Files.readAllBytes(oraclePath);

        Value got = Ktav.loads(new String(src, StandardCharsets.UTF_8));
        Value want = WireJson.decode(oracle);
        assertTrue(valueEquals(want, got),
                "mismatch for " + ktavPath + "\nsrc:\n" + new String(src, StandardCharsets.UTF_8)
                        + "\nwant: " + want + "\ngot:  " + got);
    }

    /**
     * Structural equality with one subtlety: floats compare by numeric
     * value (since the oracle JSON may use a canonical form like
     * {@code 2.5e+8} where the source Ktav had {@code 2.5E+8}). Integers
     * compare by {@link BigInteger} value (tolerates leading zeros — not
     * that any fixture has them).
     */
    private static boolean valueEquals(Value a, Value b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Value.Flt fa && b instanceof Value.Flt fb) {
            double da = Double.parseDouble(fa.text());
            double db = Double.parseDouble(fb.text());
            return Double.compare(da, db) == 0;
        }
        if (a instanceof Value.Int ia && b instanceof Value.Int ib) {
            return new BigInteger(ia.text()).equals(new BigInteger(ib.text()));
        }
        if (a instanceof Value.Arr aa && b instanceof Value.Arr ab) {
            if (aa.items().size() != ab.items().size()) return false;
            for (int i = 0; i < aa.items().size(); i++) {
                if (!valueEquals(aa.items().get(i), ab.items().get(i))) return false;
            }
            return true;
        }
        if (a instanceof Value.Obj oa && b instanceof Value.Obj ob) {
            if (oa.entries().size() != ob.entries().size()) return false;
            Iterator<Map.Entry<String, Value>> ai = oa.entries().entrySet().iterator();
            Iterator<Map.Entry<String, Value>> bi = ob.entries().entrySet().iterator();
            while (ai.hasNext()) {
                Map.Entry<String, Value> ea = ai.next();
                Map.Entry<String, Value> eb = bi.next();
                if (!ea.getKey().equals(eb.getKey())) return false;
                if (!valueEquals(ea.getValue(), eb.getValue())) return false;
            }
            return true;
        }
        return a.equals(b);
    }

    /**
     * Spec § 5.9.10 / § 5.9.8: every valid fixture ships a
     * {@code .canonical.ktav} companion holding the one spelling a
     * conforming canonical writer must produce. This is the only check
     * that compares {@link Ktav#emitCanonical} against the spec's own
     * bytes rather than against a model of it — without it, the
     * canonical writer is tested against nothing but itself.
     */
    @TestFactory
    Stream<DynamicTest> canonicalFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(failTest("valid: cabi not built", CABI_MISSING));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(failTest("valid: spec submodule missing", SPEC_MISSING));
        }
        Path root = TestPaths.SPEC.resolve("valid");
        return collectCanonicalFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        relativeName(root, p),
                        () -> runCanonical(p)));
    }

    private void runCanonical(Path canonicalPath) throws IOException {
        String name = canonicalPath.getFileName().toString();
        Path srcPath = canonicalPath.resolveSibling(
                name.substring(0, name.length() - ".canonical.ktav".length()) + ".ktav");
        assertTrue(Files.isRegularFile(srcPath),
                "canonical fixture " + canonicalPath + " has no source " + srcPath);
        byte[] want = Files.readAllBytes(canonicalPath);
        String src = new String(Files.readAllBytes(srcPath), StandardCharsets.UTF_8);

        Value parsed = Ktav.loads(src);
        byte[] got = Ktav.emitCanonical(parsed).getBytes(StandardCharsets.UTF_8);

        assertTrue(Arrays.equals(want, got),
                "canonical mismatch for " + srcPath
                        + "\nwant: " + new String(want, StandardCharsets.UTF_8)
                        + "\ngot:  " + new String(got, StandardCharsets.UTF_8));
    }

    /**
     * Spec § 8.2 {@code unrepresentable/}: the oracle names the reason
     * code, and BOTH writers must refuse with it.
     *
     * <p>No exemptions: {@code ScalarRoot} and {@code NonFiniteFloat}
     * used to be unreachable through this binding — the JSON wire
     * rejected a scalar root and a {@code {"$f":"NaN"}} payload before
     * the native writers ever saw them, leaving an opaque
     * {@code Message}-class error with {@code reason == null}.
     * {@link WriterPrecheck} now raises both with the spec's own reason
     * codes, so the oracle's reason is asserted exactly, on both writers.
     */
    @TestFactory
    Stream<DynamicTest> unrepresentableFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(failTest("unrepresentable: cabi not built", CABI_MISSING));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(failTest("unrepresentable: spec submodule missing", SPEC_MISSING));
        }
        Path root = TestPaths.SPEC.resolve("unrepresentable");
        if (!Files.isDirectory(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "unrepresentable: category directory missing",
                    () -> fail("category directory missing — runner must not silently pass an unknown/absent category")));
        }
        return collectJsonFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        relativeName(root, p),
                        () -> runUnrepresentable(p)));
    }

    private void runUnrepresentable(Path jsonPath) throws IOException {
        Value doc = WireJson.decode(Files.readAllBytes(jsonPath));
        Value.Obj top = (Value.Obj) doc;
        Value value = rewriteFloatMarkers(top.entries().get("value"));
        String reason = ((Value.Str) top.entries().get("unrepresentable_reason")).value();

        KtavException fromDumps = assertThrows(KtavException.class, () -> Ktav.dumps(value),
                "dumps must refuse " + jsonPath);
        KtavException fromCanonical = assertThrows(KtavException.class, () -> Ktav.emitCanonical(value),
                "emitCanonical must refuse " + jsonPath);

        assertEquals("UnrepresentableAt", fromDumps.getError(), "error class for " + jsonPath);
        assertEquals("UnrepresentableAt", fromCanonical.getError(),
                "error class for " + jsonPath);
        assertEquals(reason, fromDumps.getReason(), "dumps reason for " + jsonPath);
        assertEquals(reason, fromCanonical.getReason(), "emitCanonical reason for " + jsonPath);
        assertEquals("§5.9.0", fromDumps.getSpecSection(), "spec section for " + jsonPath);
        assertEquals("§5.9.0", fromCanonical.getSpecSection(), "spec section for " + jsonPath);
        if (reason.equals("ScalarRoot")) {
            // The path is empty because the offense IS the root.
            assertNotNull(fromDumps.getPath(), "dumps path for " + jsonPath);
            assertNotNull(fromCanonical.getPath(), "emitCanonical path for " + jsonPath);
            assertTrue(fromDumps.getPath().isEmpty(),
                    "ScalarRoot path must be empty: " + jsonPath);
            assertTrue(fromCanonical.getPath().isEmpty(),
                    "ScalarRoot path must be empty: " + jsonPath);
        }
    }

    /**
     * Rewrites the fixture-only {@code {"$float": "..."}} encoding of
     * non-finite floats into {@link Value.Flt}. WireJson decodes that
     * shape as an ordinary single-entry Obj whose payload is a Str; an
     * Obj with exactly one entry keyed {@code $float} whose value is a
     * Str is therefore re-interpreted as a Float. Arrays and objects
     * recurse; everything else is returned unchanged.
     */
    private static Value rewriteFloatMarkers(Value v) {
        if (v instanceof Value.Obj o) {
            if (o.entries().size() == 1) {
                Map.Entry<String, Value> only = o.entries().entrySet().iterator().next();
                if (only.getKey().equals("$float") && only.getValue() instanceof Value.Str s) {
                    return new Value.Flt(s.value());
                }
            }
            LinkedHashMap<String, Value> out = new LinkedHashMap<>();
            o.entries().forEach((k, val) -> out.put(k, rewriteFloatMarkers(val)));
            return new Value.Obj(out);
        }
        if (v instanceof Value.Arr a) {
            return new Value.Arr(a.items().stream()
                    .map(ConformanceTest::rewriteFloatMarkers)
                    .collect(Collectors.toList()));
        }
        return v;
    }

    /**
     * Spec § 8.2 {@code parseable-unrepresentable/}: the lax parser
     * accepts the document, and the canonical writer must refuse it with
     * the oracle's exact reason code — same contract as {@code
     * unrepresentable/}, only here the value was reachable through
     * {@link Ktav#loads}.
     */
    @TestFactory
    Stream<DynamicTest> parseableUnrepresentableFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(failTest("parseable-unrepresentable: cabi not built", CABI_MISSING));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(failTest(
                    "parseable-unrepresentable: spec submodule missing", SPEC_MISSING));
        }
        Path root = TestPaths.SPEC.resolve("parseable-unrepresentable");
        if (!Files.isDirectory(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "parseable-unrepresentable: category directory missing",
                    () -> fail("category directory missing — runner must not silently pass an unknown/absent category")));
        }
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        relativeName(root, p),
                        () -> runParseableUnrepresentable(p)));
    }

    private void runParseableUnrepresentable(Path ktavPath) throws IOException {
        Path oraclePath = ktavPath.resolveSibling(
                ktavPath.getFileName().toString().replaceFirst("\\.ktav$", ".json"));
        String src = new String(Files.readAllBytes(ktavPath), StandardCharsets.UTF_8);
        Value oracle = WireJson.decode(Files.readAllBytes(oraclePath));
        Value want = rewriteFloatMarkers(((Value.Obj) oracle).entries().get("value"));
        String reason = ((Value.Str) ((Value.Obj) oracle).entries()
                .get("unrepresentable_reason")).value();

        Value parsed = Ktav.loads(src);
        assertTrue(valueEquals(want, parsed),
                "parse mismatch for " + ktavPath + "\nsrc:\n" + src
                        + "\nwant: " + want + "\ngot:  " + parsed);

        KtavException fromCanonical = assertThrows(KtavException.class,
                () -> Ktav.emitCanonical(parsed),
                "emitCanonical must refuse " + ktavPath);
        assertEquals(reason, fromCanonical.getReason(),
                "emitCanonical reason for " + ktavPath);
        assertEquals("UnrepresentableAt", fromCanonical.getError(),
                "error class for " + ktavPath);
    }

    /**
     * Spec § 8.1 {@code strict-lossy/}: the lax entry point accepts the
     * fixture and yields {@code lax_value}; the strict entry point rejects
     * it with {@code LossyScalar} naming the oracle's exact body/canonical.
     */
    @TestFactory
    Stream<DynamicTest> strictLossyFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(failTest("strict-lossy: cabi not built", CABI_MISSING));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(failTest("strict-lossy: spec submodule missing", SPEC_MISSING));
        }
        Path root = TestPaths.SPEC.resolve("strict-lossy");
        if (!Files.isDirectory(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "strict-lossy: category directory missing",
                    () -> fail("category directory missing — runner must not silently pass an unknown/absent category")));
        }
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        relativeName(root, p),
                        () -> runStrictLossy(p)));
    }

    private void runStrictLossy(Path ktavPath) throws IOException {
        Path oraclePath = ktavPath.resolveSibling(
                ktavPath.getFileName().toString().replaceFirst("\\.ktav$", ".json"));
        String src = new String(Files.readAllBytes(ktavPath), StandardCharsets.UTF_8);
        Map<String, Value> oracle = ((Value.Obj) WireJson.decode(Files.readAllBytes(oraclePath))).entries();
        Value want = oracle.get("lax_value");

        Value lax = Ktav.loads(src);
        assertTrue(valueEquals(want, lax),
                "lax mismatch for " + ktavPath + "\nsrc:\n" + src
                        + "\nwant: " + want + "\ngot:  " + lax);

        KtavException e = assertThrows(KtavException.class, () -> Ktav.loadsStrict(src),
                "loadsStrict must refuse " + ktavPath);
        assertEquals(((Value.Str) oracle.get("expected_error")).value(), e.getError(),
                "error class for " + ktavPath);
        assertEquals(((Value.Str) oracle.get("body")).value(), e.getBody(),
                "body for " + ktavPath);
        assertEquals(((Value.Str) oracle.get("canonical")).value(), e.getCanonical(),
                "canonical for " + ktavPath);
    }

    /**
     * Spec § 8.1 {@code invalid/}: every fixture ships a sibling oracle
     * naming the exact error class it must produce. The expected class is
     * asserted EXACTLY against the envelope — a fixture that stops
     * failing, or starts failing differently, is a regression, not a
     * shrug.
     *
     * <p>Fixtures the manifest flags {@code raw_bytes} are deliberately
     * not valid UTF-8 (spec &sect; 6.15). The Java API takes a String, so
     * that input can never reach the parser at all: the strict byte
     * &rarr; String boundary IS the rejection point, and the oracle's
     * expectation is checked against it rather than passed silently.
     */
    @TestFactory
    Stream<DynamicTest> invalidFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(failTest("invalid: cabi not built", CABI_MISSING));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(failTest("invalid: spec submodule missing", SPEC_MISSING));
        }
        Set<String> rawBytes = CorpusManifest.read(
                TestPaths.SPEC.resolve("manifest.json")).rawByteFixtures();
        Path root = TestPaths.SPEC.resolve("invalid");
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        relativeName(root, p),
                        () -> runInvalid(p, rawBytes)));
    }

    private void runInvalid(Path ktavPath, Set<String> rawByteFixtures) throws IOException {
        Path oraclePath = ktavPath.resolveSibling(
                ktavPath.getFileName().toString().replaceFirst("\\.ktav$", ".json"));
        String expected = expectedError(oraclePath, ktavPath);
        String stem = TestPaths.SPEC.relativize(ktavPath).toString().replace('\\', '/');
        stem = stem.substring(0, stem.length() - ".ktav".length());
        byte[] raw = Files.readAllBytes(ktavPath);

        if (rawByteFixtures.contains(stem)) {
            try {
                strictDecodeUtf8(raw);
                fail("fixture " + ktavPath + " is flagged raw_bytes in manifest.json"
                        + " but decodes as UTF-8 — the flag is wrong, not the decoder");
            } catch (CharacterCodingException e) {
                // Expected: the strict decoder refuses the bytes.
            }
            assertEquals("InvalidUtf8", expected,
                    "oracle for raw_bytes fixture " + ktavPath + " must expect InvalidUtf8");
            return;
        }

        try {
            strictDecodeUtf8(raw);
        } catch (CharacterCodingException e) {
            fail("fixture " + ktavPath + " is not valid UTF-8 but is not flagged raw_bytes"
                    + " in manifest.json — add a fixture_flags entry for it");
        }
        String src = new String(raw, StandardCharsets.UTF_8);
        KtavException e = assertThrows(KtavException.class, () -> Ktav.loads(src),
                "expected parse error for " + ktavPath);
        assertEquals(expected, e.getError(), "error class for " + ktavPath);
    }

    /** The oracle's {@code {"expected_error": "...", "note": "..."}} string. */
    private static String expectedError(Path oraclePath, Path ktavPath) throws IOException {
        Value oracle = WireJson.decode(Files.readAllBytes(oraclePath));
        Value field = ((Value.Obj) oracle).entries().get("expected_error");
        if (!(field instanceof Value.Str)) {
            fail("oracle for " + ktavPath + " has no string \"expected_error\": " + oraclePath);
        }
        return ((Value.Str) field).value();
    }

    /**
     * Walks {@code root} and materialises the .ktav fixture list. The
     * Stream from {@link Files#walk} holds an open directory iterator;
     * draining inside try-with-resources closes it before we hand the
     * (now-detached) list back to JUnit.
     */
    private static List<Path> collectKtavFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".ktav")
                            && !p.getFileName().toString().endsWith(".canonical.ktav"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** Walks {@code root} and materialises the {@code .canonical.ktav} companion list. */
    private static List<Path> collectCanonicalFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".canonical.ktav"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** Walks {@code root} and materialises the {@code .json} fixture list (unrepresentable). */
    private static List<Path> collectJsonFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** Dynamic-test display name: the fixture's path relative to its category. */
    private static String relativeName(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    /**
     * A guard that fails instead of skipping. Both the cabi build and the
     * spec submodule are prerequisites for every conformance check; when
     * either is missing the honest outcome is a red test saying which
     * command fixes it, because a passing skip would report a green
     * conformance suite that verified nothing.
     */
    private static DynamicTest failTest(String name, String message) {
        return DynamicTest.dynamicTest(name, () -> fail(message));
    }

    /** Strict UTF-8 decode: fails on malformed or unmappable input. */
    private static void strictDecodeUtf8(byte[] bytes) throws CharacterCodingException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        decoder.decode(ByteBuffer.wrap(bytes));
    }
}
