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
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConformanceTest {

    private static Path specRoot() {
        String env = System.getenv("KTAV_SPEC_ROOT");
        if (env != null && !env.isEmpty()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.dir")).getParent()
                .resolve("spec").resolve("versions").resolve("0.1").resolve("tests");
    }

    private static boolean hasNative() {
        String e = System.getenv("KTAV_LIB_PATH");
        return e != null && !e.isEmpty();
    }

    @TestFactory
    Stream<DynamicTest> validFixtures() throws IOException {
        if (!hasNative()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: KTAV_LIB_PATH not set", () -> {
                    }));
        }
        Path root = specRoot().resolve("valid");
        if (!Files.exists(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: " + root + " missing", () -> {
                    }));
        }
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".ktav"))
                .sorted()
                .map(p -> DynamicTest.dynamicTest(
                        root.relativize(p).toString().replace('\\', '/'),
                        () -> runValid(p)));
    }

    private void runValid(Path ktavPath) throws IOException {
        String base = ktavPath.toString();
        Path oraclePath = Path.of(base.substring(0, base.length() - ".ktav".length()) + ".json");
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
        if (!hasNative()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: KTAV_LIB_PATH not set", () -> {
                    }));
        }
        Path root = specRoot().resolve("invalid");
        if (!Files.exists(root)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "skip: " + root + " missing", () -> {
                    }));
        }
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".ktav"))
                .sorted()
                .map(p -> DynamicTest.dynamicTest(
                        root.relativize(p).toString().replace('\\', '/'),
                        () -> runInvalid(p)));
    }

    private void runInvalid(Path ktavPath) throws IOException {
        String src = Files.readString(ktavPath, StandardCharsets.UTF_8);
        assertThrows(KtavException.class, () -> Ktav.loads(src),
                "expected parse error for " + ktavPath);
    }
}
