package lang.ktav;

import lang.ktav.internal.WireJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                    .filter(p -> p.toString().endsWith(".ktav"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private void runInvalid(Path ktavPath) throws IOException {
        String src = Files.readString(ktavPath, StandardCharsets.UTF_8);
        assertThrows(KtavException.class, () -> Ktav.loads(src),
                "expected parse error for " + ktavPath);
    }
}
