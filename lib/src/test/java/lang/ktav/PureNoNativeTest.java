package lang.ktav;

import lang.ktav.internal.NativeLoader;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Import-without-native guarantee: the ENTIRE pure backend must work
 * with NO native library present — no load, no download. We point
 * {@link NativeLoader} at a path that does not exist for the whole test
 * class; since {@code NativeLib} loads lazily through that resolver,
 * ANY native touch by the pure code paths below fails this class
 * immediately (the download fallback is likewise unreachable — the
 * override short-circuits before it).
 *
 * <p>Residual caveat, stated plainly (review round 2): this guarantee
 * holds ONLY while {@code NativeLib} has not already been initialized
 * in this JVM. Java offers no reliable way to ask whether the holder
 * class has loaded yet, so the class cannot assert its own premise.
 * What it DOES assert honestly: the tests call
 * {@link lang.ktav.pure.PureKtav} exclusively, and
 * {@link #resolverOverrideWasActuallyBogus()} proves the resolver
 * override was installed and is honored ({@code NativeLoader.resolve()}
 * with the bogus path throws). In CI, the separate {@code test-pure}
 * and {@code packaged-pure} jobs run this suite and the smoke main in
 * JVMs where no native test has ever run, which closes the ordering
 * window at the workflow level; in the full {@code test} job, JUnit's
 * class ordering is not guaranteed and this suite may run after native
 * tests — there it still proves the pure API paths make no NEW native
 * attempt (the bogus resolver would reject any), which is the strongest
 * claim honestly available.
 */
final class PureNoNativeTest {

    private static final String BOGUS = Paths.get(System.getProperty("java.io.tmpdir"),
            "ktav-no-such-native-" + UUID.randomUUID()).toString();

    @BeforeAll
    static void pointNativeLoaderNowhere() {
        NativeLoader.setLibraryPath(BOGUS);
    }

    @AfterAll
    static void restoreLoaderForOtherTests() {
        // Only meaningful in the full native CI job (cabi built): put the
        // resolver back where TestPaths left it so later native tests in
        // the same JVM are unaffected. Without a cabi there is nothing to
        // restore — native tests would fail on the missing artifact anyway.
        if (TestPaths.cabiBuilt()) {
            NativeLoader.setLibraryPath(TestPaths.CABI.toString());
        }
    }

    @Test
    void versionNeedsNoNative() {
        assertEquals("0.8.1", lang.ktav.pure.PureKtav.version());
    }

    @Test
    void parsingNeedsNoNative() {
        Value v = lang.ktav.pure.PureKtav.loads("a: 1\nb.c: two\n");
        Value.Obj top = assertInstanceOf(Value.Obj.class, v);
        assertEquals(new Value.Int("1"), top.entries().get("a"));
        Value parsedStrict = lang.ktav.pure.PureKtav.loadsStrict("a: 1\n");
        assertInstanceOf(Value.Obj.class, parsedStrict);
        // Byte entry too.
        assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads("a: 1\n".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void writingNeedsNoNative() {
        LinkedHashMap<String, Value> m = new LinkedHashMap<>();
        m.put("a", Value.Int.of(1));
        Value.Obj doc = new Value.Obj(m);
        String dumped = lang.ktav.pure.PureKtav.dumps(doc);
        assertTrue(CorpusHarness.valueEquals(doc, lang.ktav.pure.PureKtav.loads(dumped)));
        assertTrue(lang.ktav.pure.PureKtav.emitCanonical(doc).contains("a: 1"));
        assertTrue(lang.ktav.pure.PureKtav.toStringForceStrings(doc).contains("a:: 1"));
    }

    @Test
    void formattingNeedsNoNative() {
        String formatted = lang.ktav.pure.PureKtav.format("## c\na: 1\n");
        assertTrue(formatted.contains("## c"));
        assertEquals(formatted, lang.ktav.pure.PureKtav.format(formatted));
    }

    @Test
    void pureErrorsNeedNoNative() {
        assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.loads("a: ["));
        assertThrows(KtavException.class, () -> lang.ktav.pure.PureKtav.loadsStrict("a: 1.10\n"));
    }

    @Test
    void resolverOverrideWasActuallyBogus() {
        // Guard the guard: the override this class installed must NOT
        // point at a real library file, or the tests above prove nothing.
        assertTrue(!java.nio.file.Files.isRegularFile(Paths.get(BOGUS)),
                "bogus path must not exist: " + BOGUS);
        // And the resolver must still honor it: any attempt to load the
        // native library right now throws instead of silently succeeding.
        assertThrows(RuntimeException.class, NativeLoader::resolve);
    }
}
