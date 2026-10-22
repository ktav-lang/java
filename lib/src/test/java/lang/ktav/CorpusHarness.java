package lang.ktav;

import lang.ktav.internal.WireJson;

import org.junit.jupiter.api.DynamicTest;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ONE parameterized corpus runner shared by both backends
 * ({@link ConformanceTest} for native, {@code PureConformanceTest} for
 * pure): every spec § 8 category, the manifest guard, and the
 * structural value equality all live here, parameterized by
 * {@link Backend}. Assertions are identical for both implementations —
 * neither gets a weaker copy.
 */
final class CorpusHarness {

    static final String CABI_MISSING =
            "ktav_cabi is not built — run `cargo build --release -p ktav-cabi`";
    static final String SPEC_MISSING =
            "spec submodule is missing — run `git submodule update --init spec`";

    /**
     * The closed category set this runner implements (spec § 8.5, schema
     * version 1), with the EXACT fixture counts the manifest must
     * declare. A missing category, an extra category, or a wrong count
     * means the corpus is not the one this runner understands — hard
     * failure, never adaptation.
     */
    private static final Map<String, Integer> EXPECTED_CATEGORIES =
            new LinkedHashMap<>(Map.of(
                    "valid", 228,
                    "invalid", 90,
                    "unrepresentable", 5,
                    "parseable-unrepresentable", 4,
                    "strict-lossy", 13));

    /**
     * The EXACT number of fixtures the manifest must flag
     * {@code raw_bytes} (spec § 6.15). A different count is a corpus
     * shape the runner does not understand.
     */
    private static final int EXPECTED_RAW_BYTES_FLAGS = 9;

    private CorpusHarness() {
    }

    // ── Prerequisite guards: fail, never skip ───────────────────────────

    /** Corpus presence is a hard prerequisite for every category runner. */
    static void requireSpec() {
        assertTrue(TestPaths.specPresent(), SPEC_MISSING);
    }

    /** Native backend additionally requires the built cabi artifact. */
    static void requireCabiFor(Backend backend) {
        if (backend.needsNativeLibrary()) {
            assertTrue(TestPaths.cabiBuilt(), CABI_MISSING);
        }
    }

    /**
     * Spec § 8.5 manifest compliance: closed category list with EXACT
     * counts, schema_version == 1, fixture_flags ⊆ {raw_bytes} with
     * EXACTLY 9 raw_bytes entries, and a canonical companion for every
     * valid/ source. Shared by both backends' {@code @BeforeAll}.
     *
     * <p>Order of validation (documented per review round 2): inside
     * {@link CorpusManifest#read} the schema-version check runs BEFORE
     * the closed-set category/count check, so a manifest with an
     * unimplemented schema version fails on the version — the message
     * names the actual problem — while a version-1 manifest with wrong
     * categories/counts fails on the closed set.</p>
     */
    static void runManifestGuard() throws IOException {
        CorpusManifest manifest = CorpusManifest.read(
                TestPaths.SPEC.resolve("manifest.json"));

        // Canonical-companion coverage: EVERY valid/ source must have a
        // sibling .canonical.ktav (spec § 5.9.10). A missing companion
        // silently reduces canonical coverage — hard failure naming the
        // fixture. canonicalFixtures() still iterates from this same
        // source list, so this guard is exactly the completeness proof
        // for that enumeration.
        Path validRoot = TestPaths.SPEC.resolve("valid");
        for (Path src : collectKtavFiles(validRoot)) {
            String name = src.getFileName().toString();
            Path canonical = src.resolveSibling(
                    name.substring(0, name.length() - ".ktav".length())
                            + ".canonical.ktav");
            assertTrue(Files.isRegularFile(canonical),
                    "valid/ source fixture " + relativeName(validRoot, src)
                            + " has no sibling .canonical.ktav — canonical coverage is"
                            + " reduced (spec § 5.9.10) and the runner must not pass");
        }

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
     * sibling files. Stems are counted per directory, since "sibling"
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

    // ── Category runners (spec § 8) ─────────────────────────────────────

    /** Spec § 8 valid/: parse must equal the .json oracle. */
    static Stream<DynamicTest> validFixtures(Backend backend) throws IOException {
        requireCabiFor(backend);
        requireSpec();
        Path root = TestPaths.SPEC.resolve("valid");
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        backend.name() + ": " + relativeName(root, p),
                        () -> runValid(backend, p)));
    }

    private static void runValid(Backend backend, Path ktavPath) throws IOException {
        Path oraclePath = oracleFor(ktavPath);
        byte[] src = Files.readAllBytes(ktavPath);
        byte[] oracle = Files.readAllBytes(oraclePath);

        Value got = backend.loads(new String(src, StandardCharsets.UTF_8));
        Value want = WireJson.decode(oracle);
        assertTrue(valueEquals(want, got),
                "mismatch for " + ktavPath + " (" + backend.name() + ")\nsrc:\n"
                        + new String(src, StandardCharsets.UTF_8)
                        + "\nwant: " + want + "\ngot:  " + got);
    }

    /**
     * Spec § 5.9.10 / § 5.9.8: canonical bytes must equal the spec's own
     * {@code .canonical.ktav} oracle — the independent check that keeps
     * two equally wrong implementations from agreeing their way to green.
     */
    static Stream<DynamicTest> canonicalFixtures(Backend backend) throws IOException {
        requireCabiFor(backend);
        requireSpec();
        Path root = TestPaths.SPEC.resolve("valid");
        return collectCanonicalFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        backend.name() + ": " + relativeName(root, p),
                        () -> runCanonical(backend, p)));
    }

    private static void runCanonical(Backend backend, Path canonicalPath) throws IOException {
        String name = canonicalPath.getFileName().toString();
        Path srcPath = canonicalPath.resolveSibling(
                name.substring(0, name.length() - ".canonical.ktav".length()) + ".ktav");
        assertTrue(Files.isRegularFile(srcPath),
                "canonical fixture " + canonicalPath + " has no source " + srcPath);
        byte[] want = Files.readAllBytes(canonicalPath);
        String src = new String(Files.readAllBytes(srcPath), StandardCharsets.UTF_8);

        Value parsed = backend.loads(src);
        byte[] got = backend.emitCanonical(parsed).getBytes(StandardCharsets.UTF_8);

        assertTrue(Arrays.equals(want, got),
                "canonical mismatch for " + srcPath + " (" + backend.name() + ")\n"
                        + "want: " + new String(want, StandardCharsets.UTF_8)
                        + "\ngot:  " + new String(got, StandardCharsets.UTF_8));
    }

    /** Spec § 8.2 unrepresentable/: both writers refuse with the oracle's reason. */
    static Stream<DynamicTest> unrepresentableFixtures(Backend backend) throws IOException {
        requireCabiFor(backend);
        requireSpec();
        Path root = TestPaths.SPEC.resolve("unrepresentable");
        if (!Files.isDirectory(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    backend.name() + ": unrepresentable category directory missing",
                    () -> fail("category directory missing — runner must not silently pass"
                            + " an unknown/absent category")));
        }
        return collectJsonFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        backend.name() + ": " + relativeName(root, p),
                        () -> runUnrepresentable(backend, p)));
    }

    private static void runUnrepresentable(Backend backend, Path jsonPath) throws IOException {
        Value doc = WireJson.decode(Files.readAllBytes(jsonPath));
        Value.Obj top = (Value.Obj) doc;
        Value value = rewriteFloatMarkers(top.entries().get("value"));
        String reason = ((Value.Str) top.entries().get("unrepresentable_reason")).value();

        KtavException fromDumps = assertThrowsKtav(() -> backend.dumps(value),
                "dumps must refuse " + jsonPath);
        KtavException fromCanonical = assertThrowsKtav(() -> backend.emitCanonical(value),
                "emitCanonical must refuse " + jsonPath);

        assertEquals("UnrepresentableAt", fromDumps.getError(), "error class for " + jsonPath);
        assertEquals("UnrepresentableAt", fromCanonical.getError(),
                "error class for " + jsonPath);
        assertEquals(reason, fromDumps.getReason(), "dumps reason for " + jsonPath);
        assertEquals(reason, fromCanonical.getReason(), "emitCanonical reason for " + jsonPath);
        assertEquals("§5.9.0", fromDumps.getSpecSection(), "spec section for " + jsonPath);
        assertEquals("§5.9.0", fromCanonical.getSpecSection(), "spec section for " + jsonPath);
        if (reason.equals("ScalarRoot")) {
            assertNotNull(fromDumps.getPath(), "dumps path for " + jsonPath);
            assertNotNull(fromCanonical.getPath(), "emitCanonical path for " + jsonPath);
            assertTrue(fromDumps.getPath().isEmpty(),
                    "ScalarRoot path must be empty: " + jsonPath);
            assertTrue(fromCanonical.getPath().isEmpty(),
                    "ScalarRoot path must be empty: " + jsonPath);
        }
    }

    /** Spec § 8.2 parseable-unrepresentable/: lax parse, canonical refusal. */
    static Stream<DynamicTest> parseableUnrepresentableFixtures(Backend backend)
            throws IOException {
        requireCabiFor(backend);
        requireSpec();
        Path root = TestPaths.SPEC.resolve("parseable-unrepresentable");
        if (!Files.isDirectory(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    backend.name() + ": parseable-unrepresentable category directory missing",
                    () -> fail("category directory missing — runner must not silently pass"
                            + " an unknown/absent category")));
        }
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        backend.name() + ": " + relativeName(root, p),
                        () -> runParseableUnrepresentable(backend, p)));
    }

    private static void runParseableUnrepresentable(Backend backend, Path ktavPath)
            throws IOException {
        Path oraclePath = oracleFor(ktavPath);
        String src = new String(Files.readAllBytes(ktavPath), StandardCharsets.UTF_8);
        Value oracle = WireJson.decode(Files.readAllBytes(oraclePath));
        Value want = rewriteFloatMarkers(((Value.Obj) oracle).entries().get("value"));
        String reason = ((Value.Str) ((Value.Obj) oracle).entries()
                .get("unrepresentable_reason")).value();

        Value parsed = backend.loads(src);
        assertTrue(valueEquals(want, parsed),
                "parse mismatch for " + ktavPath + " (" + backend.name() + ")\nsrc:\n" + src
                        + "\nwant: " + want + "\ngot:  " + parsed);

        KtavException fromCanonical = assertThrowsKtav(() -> backend.emitCanonical(parsed),
                "emitCanonical must refuse " + ktavPath);
        assertEquals(reason, fromCanonical.getReason(),
                "emitCanonical reason for " + ktavPath);
        assertEquals("UnrepresentableAt", fromCanonical.getError(),
                "error class for " + ktavPath);
    }

    /** Spec § 8.1 strict-lossy/: lax accepts, strict rejects with body/canonical. */
    static Stream<DynamicTest> strictLossyFixtures(Backend backend) throws IOException {
        requireCabiFor(backend);
        requireSpec();
        Path root = TestPaths.SPEC.resolve("strict-lossy");
        if (!Files.isDirectory(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    backend.name() + ": strict-lossy category directory missing",
                    () -> fail("category directory missing — runner must not silently pass"
                            + " an unknown/absent category")));
        }
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        backend.name() + ": " + relativeName(root, p),
                        () -> runStrictLossy(backend, p)));
    }

    private static void runStrictLossy(Backend backend, Path ktavPath) throws IOException {
        Path oraclePath = oracleFor(ktavPath);
        String src = new String(Files.readAllBytes(ktavPath), StandardCharsets.UTF_8);
        Map<String, Value> oracle =
                ((Value.Obj) WireJson.decode(Files.readAllBytes(oraclePath))).entries();
        Value want = oracle.get("lax_value");

        Value lax = backend.loads(src);
        assertTrue(valueEquals(want, lax),
                "lax mismatch for " + ktavPath + " (" + backend.name() + ")\nsrc:\n" + src
                        + "\nwant: " + want + "\ngot:  " + lax);

        KtavException e = assertThrowsKtav(() -> backend.loadsStrict(src),
                "loadsStrict must refuse " + ktavPath);
        assertEquals(((Value.Str) oracle.get("expected_error")).value(), e.getError(),
                "error class for " + ktavPath);
        assertEquals(((Value.Str) oracle.get("body")).value(), e.getBody(),
                "body for " + ktavPath);
        assertEquals(((Value.Str) oracle.get("canonical")).value(), e.getCanonical(),
                "canonical for " + ktavPath);
    }

    /**
     * Spec § 8.1 invalid/: exact error class per oracle. Fixtures the
     * manifest flags {@code raw_bytes} are deliberately not valid UTF-8
     * (spec § 6.15): the String API rejects them at the byte → String
     * boundary; a backend with a byte[] entry (pure) must instead reject
     * them IN the parser with error {@code InvalidUtf8} and a non-empty
     * span over the raw bytes.
     */
    static Stream<DynamicTest> invalidFixtures(Backend backend) throws IOException {
        requireCabiFor(backend);
        requireSpec();
        Set<String> rawBytes = CorpusManifest.read(
                TestPaths.SPEC.resolve("manifest.json")).rawByteFixtures();
        Path root = TestPaths.SPEC.resolve("invalid");
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        backend.name() + ": " + relativeName(root, p),
                        () -> runInvalid(backend, p, rawBytes)));
    }

    private static void runInvalid(Backend backend, Path ktavPath, Set<String> rawByteFixtures)
            throws IOException {
        Path oraclePath = oracleFor(ktavPath);
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
            if (backend.hasByteEntry()) {
                KtavException e = assertThrowsKtav(() -> backend.loads(raw),
                        "byte[] entry must reject invalid UTF-8: " + ktavPath);
                assertEquals("InvalidUtf8", e.getError(),
                        "error class for raw_bytes fixture " + ktavPath);
                assertNotNull(e.getSpanStart(), "span start for " + ktavPath);
                assertNotNull(e.getSpanEnd(), "span end for " + ktavPath);
                assertTrue(e.getSpanEnd() > e.getSpanStart(),
                        "InvalidUtf8 span must be non-empty: " + ktavPath);
            }
            return;
        }

        try {
            strictDecodeUtf8(raw);
        } catch (CharacterCodingException e) {
            fail("fixture " + ktavPath + " is not valid UTF-8 but is not flagged raw_bytes"
                    + " in manifest.json — add a fixture_flags entry for it");
        }
        String src = new String(raw, StandardCharsets.UTF_8);
        KtavException e = assertThrowsKtav(() -> backend.loads(src),
                "expected parse error for " + ktavPath);
        assertEquals(expected, e.getError(), "error class for " + ktavPath);
    }

    // ── Shared helpers ──────────────────────────────────────────────────

    private static Path oracleFor(Path ktavPath) {
        return ktavPath.resolveSibling(
                ktavPath.getFileName().toString().replaceFirst("\\.ktav$", ".json"));
    }

    private static String expectedError(Path oraclePath, Path ktavPath) throws IOException {
        Value oracle = WireJson.decode(Files.readAllBytes(oraclePath));
        Value field = ((Value.Obj) oracle).entries().get("expected_error");
        if (!(field instanceof Value.Str)) {
            fail("oracle for " + ktavPath + " has no string \"expected_error\": " + oraclePath);
        }
        return ((Value.Str) field).value();
    }

    /** Fixture-only {@code {"$float": ...}} markers become {@link Value.Flt}. */
    static Value rewriteFloatMarkers(Value v) {
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
                    .map(CorpusHarness::rewriteFloatMarkers)
                    .collect(Collectors.toList()));
        }
        return v;
    }

    /**
     * Structural equality; floats numerically, integers by
     * {@link BigInteger} (oracle spellings may differ), objects in
     * insertion order.
     */
    static boolean valueEquals(Value a, Value b) {
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

    static List<Path> collectKtavFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".ktav")
                            && !p.getFileName().toString().endsWith(".canonical.ktav"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    static List<Path> collectCanonicalFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".canonical.ktav"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    static List<Path> collectJsonFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    static String relativeName(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    private static KtavException assertThrowsKtav(Runnable action, String message) {
        try {
            action.run();
        } catch (KtavException e) {
            return e;
        }
        throw new AssertionError(message);
    }

    /** Strict UTF-8 decode: fails on malformed or unmappable input. */
    static void strictDecodeUtf8(byte[] bytes) throws CharacterCodingException {
        java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        decoder.decode(ByteBuffer.wrap(bytes));
    }

    /**
     * Spec § 8.5 corpus manifest: {@code schema_version} (this runner
     * implements 1), the closed {@code categories} map with each
     * category's EXACT fixture count, and {@code fixture_flags} — of
     * which only {@code raw_bytes} (spec § 6.15) is defined today.
     * Parsed with Jackson streaming; a malformed manifest fails rather
     * than degrades.
     */
    static final class CorpusManifest {

        private static final com.fasterxml.jackson.core.JsonFactory FACTORY =
                new com.fasterxml.jackson.core.JsonFactory();

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
                        + " — the normative corpus inventory (spec § 8.5) comes with the"
                        + " spec submodule");
            }
            long schemaVersion = -1;
            Map<String, Integer> categories = new LinkedHashMap<>();
            Set<String> rawBytes = new LinkedHashSet<>();
            try (com.fasterxml.jackson.core.JsonParser p =
                    FACTORY.createParser(Files.readAllBytes(path))) {
                if (p.nextToken() != com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
                    fail("manifest.json is not a JSON object: " + path);
                }
                com.fasterxml.jackson.core.JsonToken t;
                while ((t = p.nextToken()) != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                    if (t != com.fasterxml.jackson.core.JsonToken.FIELD_NAME) {
                        fail("malformed manifest.json (expected a field name): " + path);
                    }
                    switch (p.currentName()) {
                        case "schema_version" ->
                                schemaVersion = readInt(p, "schema_version", path);
                        case "categories" -> readCategories(p, categories, path);
                        case "fixture_flags" -> readFixtureFlags(p, rawBytes, path);
                        default -> {
                            // Forward compatibility, same as the error
                            // envelope: skip what a newer manifest adds.
                            p.nextToken();
                            p.skipChildren();
                        }
                    }
                }
            }
            if (schemaVersion == -1) {
                fail("manifest.json has no schema_version: " + path);
            }
            // Documented order (review round 2): the schema-version
            // rejection runs FIRST so temp manifests written to test the
            // version check still fail on the version, not on the
            // closed-set assertions that follow.
            if (schemaVersion != 1) {
                fail("manifest.json schema_version " + schemaVersion
                        + " is not implemented by this runner (1) — refusing to guess at an"
                        + " unrecognised shape");
            }
            // Closed set: exactly the categories this runner implements,
            // with exactly the counts it was validated against.
            if (!EXPECTED_CATEGORIES.equals(categories)) {
                fail("manifest.json categories/counts " + categories
                        + " do not match the closed set this runner implements: expected"
                        + " exactly " + EXPECTED_CATEGORIES
                        + " (spec § 8.5, schema_version 1) — a missing category, an extra"
                        + " category, or a wrong count is a corpus the runner does not"
                        + " understand: " + path);
            }
            // fixture_flags: exactly EXPECTED_RAW_BYTES_FLAGS raw_bytes
            // flags — a different number is a corpus the runner does not
            // understand.
            assertTrue(rawBytes.size() == EXPECTED_RAW_BYTES_FLAGS,
                    "manifest.json flags " + rawBytes.size() + " fixture(s) raw_bytes but this"
                            + " runner implements exactly " + EXPECTED_RAW_BYTES_FLAGS
                            + " (spec § 6.15): " + path);
            if (categories.isEmpty()) {
                fail("manifest.json declares no categories: " + path);
            }
            return new CorpusManifest(categories, rawBytes);
        }

        private static long readInt(com.fasterxml.jackson.core.JsonParser p, String field,
                Path path) throws IOException {
            if (p.nextToken() != com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT) {
                fail("manifest.json field \"" + field + "\" must be an integer: " + path);
            }
            return p.getLongValue();
        }

        private static String readString(com.fasterxml.jackson.core.JsonParser p, String field,
                Path path) throws IOException {
            if (p.nextToken() != com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
                fail("manifest.json field \"" + field + "\" must be a string: " + path);
            }
            return p.getText();
        }

        private static void readCategories(
                com.fasterxml.jackson.core.JsonParser p, Map<String, Integer> out, Path path)
                throws IOException {
            if (p.nextToken() != com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
                fail("manifest.json \"categories\" must be an object: " + path);
            }
            com.fasterxml.jackson.core.JsonToken t;
            while ((t = p.nextToken()) != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                if (t != com.fasterxml.jackson.core.JsonToken.FIELD_NAME) {
                    fail("malformed manifest.json \"categories\" object: " + path);
                }
                String name = p.currentName();
                if (p.nextToken() != com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
                    fail("manifest.json category \"" + name + "\" must be an object: " + path);
                }
                long count = -1;
                com.fasterxml.jackson.core.JsonToken ft;
                while ((ft = p.nextToken()) != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                    if (ft != com.fasterxml.jackson.core.JsonToken.FIELD_NAME) {
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
                com.fasterxml.jackson.core.JsonParser p, Set<String> rawByteStems, Path path)
                throws IOException {
            if (p.nextToken() != com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
                fail("manifest.json \"fixture_flags\" must be an array: " + path);
            }
            com.fasterxml.jackson.core.JsonToken t = p.nextToken();
            while (t != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                if (t != com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
                    fail("malformed manifest.json \"fixture_flags\" entry: " + path);
                }
                String category = null;
                String fixture = null;
                Set<String> flags = Set.of();
                com.fasterxml.jackson.core.JsonToken ft;
                while ((ft = p.nextToken()) != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                    if (ft != com.fasterxml.jackson.core.JsonToken.FIELD_NAME) {
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
                // Only raw_bytes is defined (spec § 6.15). An unknown
                // flag means semantics this runner does not implement —
                // failing closed, not silently skipping the fixture.
                Set<String> unknown = new LinkedHashSet<>(flags);
                unknown.removeAll(Set.of("raw_bytes"));
                assertTrue(unknown.isEmpty(),
                        "manifest.json flags fixture " + category + "/" + fixture
                                + " with unimplemented flag(s) " + unknown
                                + " — only raw_bytes is defined (spec § 6.15): " + path);
                if (flags.contains("raw_bytes")) {
                    rawByteStems.add(category + "/" + fixture);
                }
                t = p.nextToken();
            }
        }

        private static Set<String> readFlagList(
                com.fasterxml.jackson.core.JsonParser p, Path path) throws IOException {
            if (p.nextToken() != com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
                fail("manifest.json fixture_flags \"flags\" must be an array: " + path);
            }
            Set<String> flags = new LinkedHashSet<>();
            com.fasterxml.jackson.core.JsonToken t = p.nextToken();
            while (t != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                if (t != com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
                    fail("manifest.json fixture_flags \"flags\" must be strings: " + path);
                }
                flags.add(p.getText());
                t = p.nextToken();
            }
            return flags;
        }
    }
}
