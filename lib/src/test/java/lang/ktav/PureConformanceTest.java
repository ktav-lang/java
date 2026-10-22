package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Pure-backend conformance: ALL FIVE corpus categories through the SAME
 * shared {@link CorpusHarness} the native {@link ConformanceTest} uses —
 * identical assertions, zero native code involved. No cabi build is
 * required or consulted; a missing corpus FAILS ({@code @BeforeAll}),
 * it never skips.
 */
final class PureConformanceTest {

    private static final Backend PURE = Backend.pureBackend();

    @BeforeAll
    static void validateCorpusBeforeFixtures() throws IOException {
        CorpusHarness.requireSpec();
        CorpusHarness.runManifestGuard();
    }

    @TestFactory
    Stream<DynamicTest> validFixtures() throws IOException {
        return CorpusHarness.validFixtures(PURE);
    }

    @TestFactory
    Stream<DynamicTest> canonicalFixtures() throws IOException {
        return CorpusHarness.canonicalFixtures(PURE);
    }

    @TestFactory
    Stream<DynamicTest> unrepresentableFixtures() throws IOException {
        return CorpusHarness.unrepresentableFixtures(PURE);
    }

    @TestFactory
    Stream<DynamicTest> parseableUnrepresentableFixtures() throws IOException {
        return CorpusHarness.parseableUnrepresentableFixtures(PURE);
    }

    @TestFactory
    Stream<DynamicTest> strictLossyFixtures() throws IOException {
        return CorpusHarness.strictLossyFixtures(PURE);
    }

    @TestFactory
    Stream<DynamicTest> invalidFixtures() throws IOException {
        return CorpusHarness.invalidFixtures(PURE);
    }
}
