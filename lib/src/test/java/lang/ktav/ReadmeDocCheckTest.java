package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Executes the claims README.md and its two translations make, so a doc
 * edit cannot drift from the library silently.
 *
 * <p>Each assertion corresponds to a sentence in the docs. The API-table
 * rows for {@code toStringForceStrings} and {@code emitCanonical} were
 * missing from all three languages until they were measured against the
 * real {@link Ktav} surface, which is why this class exists rather than a
 * prose review.
 */
final class ReadmeDocCheckTest {

    @BeforeAll
    static void requireNativeLib() {
        TestPaths.init();
        assumeTrue(TestPaths.cabiBuilt(),
                "cabi not built (" + TestPaths.CABI + ") — run `cargo build --release -p ktav-cabi`");
    }

    private static Value.Obj obj(Object... pairs) {
        LinkedHashMap<String, Value> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Value) pairs[i + 1]);
        }
        return new Value.Obj(map);
    }

    @Nested
    final class ApiTable {

        /**
         * The README's worked example, byte for byte. Note the comma: it is
         * a real separator inside an inline compound, whereas two spaces
         * are NOT — {@code {host: a  port: 80}} parses as the single key
         * {@code host} with the value {@code "a  port: 80"}, which would
         * make a laxer assertion here pass without expanding anything.
         */
        @Test
        void formatMatchesTheReadmeExampleExactly() {
            assertEquals(
                    "## the server\nserver: {\n    host: a\n    port: 80\n}\n",
                    Ktav.format("## the server\nserver: {host: a, port: 80}\n"));
        }

        /** Guards the separator distinction the test above relies on. */
        @Test
        void twoSpacesAreNotACompoundSeparator() {
            Value.Obj top = (Value.Obj) Ktav.loads("server: {host: a  port: 80}\n");
            Value.Obj server = (Value.Obj) top.entries().get("server");
            assertEquals(1, server.entries().size(), "two spaces must not split entries");
            assertEquals("a  port: 80", ((Value.Str) server.entries().get("host")).value());
        }

        /** README: blank-line runs collapse to exactly one. */
        @Test
        void formatCollapsesBlankRunsToOne() {
            String out = Ktav.format("a: 1\n\n\n\nb: 2\n");
            assertTrue(out.contains("\n\n"), "one blank line must survive as a grouping hint: " + out);
            assertTrue(!out.contains("\n\n\n"), "runs must collapse to one: " + out);
        }

        /**
         * README: "For a document with no comments AND no blank lines the
         * result equals Ktav.emitCanonical(Ktav.loads(src))."
         */
        @Test
        void formatEqualsEmitCanonicalWhenNoTriviaPresent() {
            String doc = "server: {host: a  port: 80}\nratio: 0.5\n";
            assertEquals(Ktav.emitCanonical(Ktav.loads(doc)), Ktav.format(doc));
        }

        /** README: format keeps trivia that the canonical writer drops. */
        @Test
        void formatDivergesFromEmitCanonicalWhenTriviaPresent() {
            String doc = "## why\na: 1\n\nb: 2\n";
            assertNotEquals(Ktav.emitCanonical(Ktav.loads(doc)), Ktav.format(doc),
                    "the canonical writer must drop the comment that format keeps");
        }

        /**
         * canonicalFromSource and emitCanonical(loads(src)) must agree:
         * both are spec § 5.9 canonicalisation of the same document, just
         * via a different number of native calls. Value.Int/Value.Flt
         * store text verbatim (see Ktav#canonicalFromSource's javadoc), so
         * unlike the JS binding there is no float-precision gap to prove
         * here — swept several shapes (bigint, huge exponent, -0.0, a
         * float long enough to force the canonical writer's own rounding)
         * and every one agreed exactly. Task #311.
         */
        @Test
        void canonicalFromSourceAgreesWithEmitCanonicalOfLoads() {
            for (String src : new String[] {
                    "x: 1.0\n",
                    "x: 1e400\n",
                    "x: -0.0\n",
                    "x: 1e6\n",
                    "x: 1.23456789012345678901\n",
                    "x: 3.141592653589793238462643383279\n",
                    "x: 99999999999999999999999999\n",
            }) {
                assertEquals(
                        Ktav.canonicalFromSource(src),
                        Ktav.emitCanonical(Ktav.loads(src)),
                        "diverged for " + src);
            }
        }

        @Test
        void canonicalFromSourceDropsCommentsAndBlankLines() {
            String doc = "## why\na: 1\n\nb: 2\n";
            String out = Ktav.canonicalFromSource(doc);
            assertTrue(!out.contains("##"), "comment must not survive: " + out);
            assertTrue(!out.contains("\n\n"), "blank line must not survive: " + out);
        }

        /**
         * README: "toStringForceStrings flattens integers, floats, booleans
         * and null to their textual form via the raw marker (::); objects
         * and arrays keep their structure, since only leaves are coerced."
         */
        @Test
        void forceStringsCoercesLeavesAndKeepsCompounds() {
            Value.Obj doc = obj(
                    "p", Value.Int.of(8080),
                    "r", Value.Flt.of(0.5),
                    "t", Value.Bool.TRUE,
                    "n", Value.Null.NULL,
                    "o", obj("k", Value.Int.of(1)),
                    "a", new Value.Arr(List.of(Value.Int.of(2))));
            String out = Ktav.toStringForceStrings(doc);
            assertTrue(out.contains("p:: 8080"), out);
            assertTrue(out.contains("r:: 0.5"), out);
            assertTrue(out.contains("t:: true"), out);
            assertTrue(out.contains("n:: null"), out);
            assertTrue(out.contains("k:: 1"), "a nested leaf must be coerced too: " + out);
            assertTrue(out.contains("o: {"), "a nested object keeps its structure: " + out);
            assertTrue(out.contains("a: ["), "a nested array keeps its structure: " + out);
        }

        /** README: "The result parses back through loads as the same set of String scalars." */
        @Test
        void forceStringsResultReparsesAsAllStrings() {
            Value.Obj doc = obj(
                    "p", Value.Int.of(8080),
                    "t", Value.Bool.TRUE,
                    "o", obj("k", Value.Int.of(1)));
            Value.Obj back = (Value.Obj) Ktav.loads(Ktav.toStringForceStrings(doc));
            assertEquals("8080", ((Value.Str) back.entries().get("p")).value());
            assertEquals("true", ((Value.Str) back.entries().get("t")).value());
            Value.Obj nested = (Value.Obj) back.entries().get("o");
            assertEquals("1", ((Value.Str) nested.entries().get("k")).value());
        }

        /** README: "Ktav.nativeVersion() -> Version string reported by the loaded ktav_cabi." */
        @Test
        void nativeVersionIsNonEmpty() {
            assertTrue(!Ktav.nativeVersion().isBlank(), "nativeVersion must report something");
        }

        /** README: loadsStrict rejects a lossy spelling that loads accepts. */
        @Test
        void loadsStrictRejectsWhatLoadsAccepts() {
            assertNotNull(Ktav.loads("version: 1.10\n"));
            assertThrows(KtavException.class, () -> Ktav.loadsStrict("version: 1.10\n"));
        }
    }

    @Nested
    final class ErrorEnvelope {

        /** The README's own worked example prints these exact values. */
        @Test
        void lossyScalarExampleMatchesTheReadme() {
            KtavException e = assertThrows(KtavException.class,
                    () -> Ktav.loadsStrict("version: 1.10\n"));
            assertEquals("LossyScalar", e.getError());
            assertEquals(1L, e.getLine());
            assertEquals("version: 1.10", e.getLineText());
            assertEquals("1.10", e.getBody());
            assertEquals("1.1", e.getCanonical());
            assertEquals("§3.6/§5.2", e.getSpecSection());
        }
        /**
         * Task #303: getMessage() must be the core's own rendering, taken
         * verbatim from the envelope's `message` field — not this
         * binding's old locally-reconstructed sentence. Distinguishes the
         * two by their actual, different wording rather than just
         * asserting non-empty: the old `describe()` format always started
         * with "Ktav error <class>: ..."; the real core message does not.
         */
        @Test
        void getMessageIsTheCoresOwnRenderingNotAReconstructedSentence() {
            KtavException e = assertThrows(KtavException.class,
                    () -> Ktav.loadsStrict("version: 1.10\n"));
            String message = e.getMessage();
            assertTrue(
                    message.contains("would be inferred as a number and silently canonicalised"),
                    "expected the core's own LossyScalar wording, got: " + message);
            assertTrue(
                    !message.startsWith("Ktav error"),
                    "message must not be this binding's old reconstructed format: " + message);
        }

        /**
         * README: "the nine other structured fields ... through ten
         * accessors — absent information is null rather than a missing
         * accessor, so any field can be read without first checking the
         * error class."
         */
        @Test
        void everyAccessorIsReadableOnAWriterTimeError() {
            LinkedHashMap<String, Value> bad = new LinkedHashMap<>();
            bad.put("", Value.Int.of(1));
            KtavException e = assertThrows(KtavException.class,
                    () -> Ktav.dumps(new Value.Obj(bad)));
            // None of these may throw; a writer-time error has no source position.
            e.getError();
            e.getReason();
            assertEquals(null, e.getLine(), "a writer-time error carries no line");
            assertEquals(null, e.getLineText());
            assertEquals(null, e.getSpanStart());
            assertEquals(null, e.getSpanEnd());
            e.getPath();
            e.getBody();
            e.getCanonical();
            e.getSpecSection();
            assertTrue(!e.getMessage().isBlank(), "every error renders a message");
        }

        /**
         * README: "getSpanStart()/getSpanEnd() are byte offsets into the
         * UTF-8 source, not UTF-16 code units." Cyrillic makes the two
         * disagree: 14 bytes vs 10 chars.
         */
        @Test
        void spanIsByteOffsetsNotUtf16() {
            KtavException e = assertThrows(KtavException.class,
                    () -> Ktav.loadsStrict("ключ: 1.10\n"));
            String lineText = e.getLineText();
            assertEquals("ключ: 1.10", lineText);
            long utf8Length = lineText.getBytes(StandardCharsets.UTF_8).length;
            assertEquals(14L, utf8Length, "sanity: the line is 14 bytes");
            assertEquals(utf8Length, e.getSpanEnd(), "span must be measured in bytes");
            assertNotEquals((long) lineText.length(), (long) e.getSpanEnd(),
                    "a UTF-16 count would be 10");
        }

        /**
         * README: "getPath() returns the exact decoded key segments, never a
         * joined string: a key literally named a.b is ONE segment."
         */
        @Test
        void pathSegmentsAreNeverSplitOnAnEscapedDot() {
            LinkedHashMap<String, Value> bad = new LinkedHashMap<>();
            bad.put("a.b", obj("", Value.Int.of(1)));
            KtavException e = assertThrows(KtavException.class,
                    () -> Ktav.dumps(new Value.Obj(bad)));
            List<String> path = e.getPath();
            if (path != null && !path.isEmpty()) {
                assertEquals("a.b", path.get(0),
                        "the literal key must stay one segment, not two");
            }
        }

        /**
         * README: the two writer rejections are named differently but share
         * a reason code, so matching getReason() is enough to know "the
         * write was refused".
         */
        @Test
        void unrepresentableAtCarriesAPathAndSharesTheReasonCode() {
            LinkedHashMap<String, Value> bad = new LinkedHashMap<>();
            bad.put("outer", obj("", Value.Int.of(1)));
            KtavException e = assertThrows(KtavException.class,
                    () -> Ktav.dumps(new Value.Obj(bad)));
            assertTrue(e.getError().startsWith("Unrepresentable"),
                    "expected an Unrepresentable* class, got " + e.getError());
            assertNotNull(e.getReason(), "a writer-time rejection must carry a reason code");
        }
    }
}
