package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-backend round trips: parse with one backend, render with the
 * OTHER, and require the Value trees (and, where the spec pins bytes,
 * the bytes) to agree. Both implementations must also agree with the
 * spec's own {@code .canonical.ktav} oracles — the independent
 * third-party check in {@link CorpusHarness#canonicalFixtures} is what
 * stops two equally wrong writers from agreeing their way to green.
 */
final class CrossBackendTest {

    private static final Backend NATIVE = Backend.nativeBackend();
    private static final Backend PURE = Backend.pureBackend();

    @BeforeAll
    static void requireBothBackends() {
        assertTrue(TestPaths.cabiBuilt(), CorpusHarness.CABI_MISSING);
        assertTrue(TestPaths.specPresent(), CorpusHarness.SPEC_MISSING);
    }

    private static Stream<Path> validSources() throws IOException {
        return CorpusHarness.collectKtavFiles(TestPaths.SPEC.resolve("valid")).stream();
    }

    /** native parse → pure dumps → both backends re-parse: same tree. */
    @TestFactory
    Stream<DynamicTest> nativeToPureRoundTrip() throws IOException {
        return validSources()
                .map(p -> DynamicTest.dynamicTest(
                        "native→pure: " + CorpusHarness.relativeName(TestPaths.SPEC, p),
                        () -> roundTrip(NATIVE, PURE, p)));
    }

    /** pure parse → native dumps → both backends re-parse: same tree. */
    @TestFactory
    Stream<DynamicTest> pureToNativeRoundTrip() throws IOException {
        return validSources()
                .map(p -> DynamicTest.dynamicTest(
                        "pure→native: " + CorpusHarness.relativeName(TestPaths.SPEC, p),
                        () -> roundTrip(PURE, NATIVE, p)));
    }

    private static void roundTrip(Backend reader, Backend writer, Path ktavPath)
            throws IOException {
        String src = new String(Files.readAllBytes(ktavPath), StandardCharsets.UTF_8);
        Value parsed = reader.loads(src);
        String rendered = writer.dumps(parsed);
        Value backFromReader = reader.loads(rendered);
        Value backFromWriter = writer.loads(rendered);
        assertTrue(CorpusHarness.valueEquals(parsed, backFromReader),
                "reader re-parse mismatch for " + ktavPath
                        + " (read " + reader.name() + ", wrote " + writer.name() + ")\n"
                        + rendered);
        assertTrue(CorpusHarness.valueEquals(parsed, backFromWriter),
                "writer re-parse mismatch for " + ktavPath
                        + " (read " + reader.name() + ", wrote " + writer.name() + ")\n"
                        + rendered);
    }

    /** canonicalFromSource must be byte-identical across backends. */
    @TestFactory
    Stream<DynamicTest> canonicalFromSourceByteEquality() throws IOException {
        return validSources()
                .map(p -> DynamicTest.dynamicTest(
                        "canonicalFromSource: " + CorpusHarness.relativeName(TestPaths.SPEC, p),
                        () -> {
                            String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                            byte[] fromNative =
                                    NATIVE.canonicalFromSource(src).getBytes(StandardCharsets.UTF_8);
                            byte[] fromPure =
                                    PURE.canonicalFromSource(src).getBytes(StandardCharsets.UTF_8);
                            assertTrue(java.util.Arrays.equals(fromNative, fromPure),
                                    "canonicalFromSource bytes differ for " + p);
                        }));
    }

    /**
     * format on trivia-free sources (no comments, no blank lines) must be
     * byte-identical across backends AND equal canonicalFromSource — the
     * formatter's documented contract. Commented fixtures are exercised
     * per-backend by the fixed-point check in {@code PureApiTest} /
     * {@code SmokeTest}; byte parity there depends on trivia attachment,
     * which the corpus pins via the canonical oracles instead.
     */
    @TestFactory
    Stream<DynamicTest> formatByteEqualityOnTriviaFreeSources() throws IOException {
        return validSources()
                .filter(CrossBackendTest::triviaFree)
                .map(p -> DynamicTest.dynamicTest(
                        "format: " + CorpusHarness.relativeName(TestPaths.SPEC, p),
                        () -> {
                            String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                            byte[] fromNative = NATIVE.format(src).getBytes(StandardCharsets.UTF_8);
                            byte[] fromPure = PURE.format(src).getBytes(StandardCharsets.UTF_8);
                            assertTrue(java.util.Arrays.equals(fromNative, fromPure),
                                    "format bytes differ for " + p);
                            byte[] canonical =
                                    NATIVE.canonicalFromSource(src).getBytes(StandardCharsets.UTF_8);
                            assertTrue(java.util.Arrays.equals(canonical, fromNative),
                                    "trivia-free format must equal canonical for " + p);
                        }));
    }

    private static boolean triviaFree(Path p) throws IOException {
        byte[] raw = Files.readAllBytes(p);
        for (byte b : raw) {
            if (b == '#') return false;
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        return !text.contains("\n\n") && !text.endsWith("\n\n") && !text.contains("\r");
    }
}
