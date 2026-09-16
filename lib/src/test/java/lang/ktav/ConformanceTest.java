package lang.ktav;

import lang.ktav.internal.WireJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class ConformanceTest {

    static {
        TestPaths.init();
    }

    @TestFactory
    Stream<DynamicTest> validFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: cabi not built", () -> {
                    }));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: spec submodule missing", () -> {
                    }));
        }
        Path root = TestPaths.SPEC.resolve("valid");
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        root.relativize(p).toString().replace('\\', '/'),
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

    @TestFactory
    Stream<DynamicTest> unrepresentableFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: cabi not built", () -> {
                    }));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: spec submodule missing", () -> {
                    }));
        }
        Path root = TestPaths.SPEC.resolve("unrepresentable");
        if (!Files.isDirectory(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "unrepresentable: category directory missing",
                    () -> fail("category directory missing — runner must not silently pass an unknown/absent category")));
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .map(p -> DynamicTest.dynamicTest(
                            root.relativize(p).toString().replace('\\', '/'),
                            () -> runUnrepresentable(p)))
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    private void runUnrepresentable(Path jsonPath) throws IOException {
        byte[] oracle = Files.readAllBytes(jsonPath);
        Value doc = WireJson.decode(oracle);
        Value.Obj top = (Value.Obj) doc;
        Value value = rewriteFloatMarkers(top.entries().get("value"));
        String reason = ((Value.Str) top.entries().get("unrepresentable_reason")).value();

        KtavException fromDumps = assertThrows(KtavException.class, () -> Ktav.dumps(value),
                "dumps must refuse " + jsonPath);
        KtavException fromCanonical = assertThrows(KtavException.class, () -> Ktav.emitCanonical(value),
                "emitCanonical must refuse " + jsonPath);
        if (reason.equals("ScalarRoot")) {
            // scalar_root never reaches the crate: the binding's own cabi
            // layer rejects non-Object/non-Array top-level values first,
            // with its own message ("top-level Ktav document must be an
            // object or array"). Refusal semantics are equivalent, only
            // the message differs — so we must not assert "ScalarRoot".
        } else {
            // NonFiniteFloat: both writers go through the cabi JSON-wire
            // input path, whose validate_float rejects the
            // `{"$f":"NaN"}` payload earlier ("$f payload must contain
            // '.' or exponent") — refusal semantics equivalent, the
            // crate's reason code is not surfaced through this binding.
            boolean surfacesReason = !reason.equals("NonFiniteFloat");
            if (surfacesReason) {
                assertTrue(fromDumps.getMessage().contains(reason),
                        "dumps message for " + jsonPath + " lacks reason " + reason
                                + ": " + fromDumps.getMessage());
                assertTrue(fromCanonical.getMessage().contains(reason),
                        "emitCanonical message for " + jsonPath + " lacks reason " + reason
                                + ": " + fromCanonical.getMessage());
            }
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

    @TestFactory
    Stream<DynamicTest> parseableUnrepresentableFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: cabi not built", () -> {
                    }));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: spec submodule missing", () -> {
                    }));
        }
        Path root = TestPaths.SPEC.resolve("parseable-unrepresentable");
        if (!Files.isDirectory(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "parseable-unrepresentable: category directory missing",
                    () -> fail("category directory missing — runner must not silently pass an unknown/absent category")));
        }
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        root.relativize(p).toString().replace('\\', '/'),
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
        assertTrue(fromCanonical.getMessage().contains(reason),
                "emitCanonical message for " + ktavPath + " lacks reason " + reason
                        + ": " + fromCanonical.getMessage());
    }

    @TestFactory
    Stream<DynamicTest> invalidFixtures() throws IOException {
        if (!TestPaths.cabiBuilt()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: cabi not built", () -> {
                    }));
        }
        if (!TestPaths.specPresent()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: spec submodule missing", () -> {
                    }));
        }
        Path root = TestPaths.SPEC.resolve("invalid");
        return collectKtavFiles(root).stream()
                .map(p -> DynamicTest.dynamicTest(
                        root.relativize(p).toString().replace('\\', '/'),
                        () -> runInvalid(p)));
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

    private void runInvalid(Path ktavPath) throws IOException {
        byte[] raw = Files.readAllBytes(ktavPath);
        try {
            strictDecodeUtf8(raw);
        } catch (CharacterCodingException e) {
            // Spec §6.15 (InvalidUtf8): the fixture's raw bytes are not
            // valid UTF-8. The Java API takes a String, so this input can
            // never even reach the parser — strict decoding failing here
            // IS the expected rejection, and the test passes at this
            // boundary.
            return;
        }
        String src = new String(raw, StandardCharsets.UTF_8);
        assertThrows(KtavException.class, () -> Ktav.loads(src),
                "expected parse error for " + ktavPath);
    }

    /** Strict UTF-8 decode: fails on malformed or unmappable input. */
    private static void strictDecodeUtf8(byte[] bytes) throws CharacterCodingException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        decoder.decode(ByteBuffer.wrap(bytes));
    }
}
