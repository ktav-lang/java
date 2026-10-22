package lang.ktav;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public-API tests for the pure backend ({@code PureKtav}) that are
 * deliberately NOT corpus fixtures: entry-point contracts, versioning,
 * null handling, constructed-Value writers, error envelope shapes.
 * ({@code PureConformanceTest} covers the corpus; this covers the API.)
 */
final class PureApiTest {

    // ── Version ─────────────────────────────────────────────────────────

    @Test
    void versionMatchesImplementedSpec() {
        assertEquals("0.8.1", lang.ktav.pure.PureKtav.version());
    }

    // ── Entry points: byte[] vs String agree ────────────────────────────

    @Test
    void byteAndStringEntriesAgree() {
        String src = "a: 1\nb: two\n";
        assertEquals(
                lang.ktav.pure.PureKtav.loads(src),
                lang.ktav.pure.PureKtav.loads(src.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loadsStrict(
                        "a: 1.10\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> lang.ktav.pure.PureKtav.loads((String) null));
        assertThrows(NullPointerException.class,
                () -> lang.ktav.pure.PureKtav.loads((byte[]) null));
        assertThrows(NullPointerException.class,
                () -> lang.ktav.pure.PureKtav.loadsStrict((String) null));
        assertThrows(NullPointerException.class, () -> lang.ktav.pure.PureKtav.dumps(null));
        assertThrows(NullPointerException.class,
                () -> lang.ktav.pure.PureKtav.toStringForceStrings(null));
        assertThrows(NullPointerException.class, () -> lang.ktav.pure.PureKtav.emitCanonical(null));
        assertThrows(NullPointerException.class,
                () -> lang.ktav.pure.PureKtav.canonicalFromSource(null));
        assertThrows(NullPointerException.class, () -> lang.ktav.pure.PureKtav.format(null));
    }

    // ── Parse semantics (i64 / binary64 domains, dotted keys) ───────────

    @Test
    void integerDomainIsI64WithFallbackToString() {
        Value.Obj top = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads("small: 42\nhuge: 99999999999999999999\n"));
        assertEquals(new Value.Int("42"), top.entries().get("small"));
        // i64 overflow falls through to String (§ 5.2 rule 13).
        assertInstanceOf(Value.Str.class, top.entries().get("huge"));
    }

    @Test
    void dottedKeysMergeIntoNestedObjects() {
        Value.Obj top = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads("a.b.c: 1\na.b.d: 2\n"));
        Value.Obj a = assertInstanceOf(Value.Obj.class, top.entries().get("a"));
        Value.Obj ab = assertInstanceOf(Value.Obj.class, a.entries().get("b"));
        assertEquals(new Value.Int("1"), ab.entries().get("c"));
        assertEquals(new Value.Int("2"), ab.entries().get("d"));
    }

    // ── Writers over CONSTRUCTED values ─────────────────────────────────

    private static Value.Obj sampleDoc() {
        LinkedHashMap<String, Value> entries = new LinkedHashMap<>();
        entries.put("name", new Value.Str("demo"));
        entries.put("count", Value.Int.of(42));
        entries.put("ratio", Value.Flt.of(0.5));
        entries.put("flag", Value.Bool.TRUE);
        entries.put("nothing", Value.Null.NULL);
        LinkedHashMap<String, Value> nested = new LinkedHashMap<>();
        nested.put("inner", Value.Int.of(1));
        entries.put("nested", new Value.Obj(nested));
        return new Value.Obj(entries);
    }

    @Test
    void dumpsRoundTripsConstructedDocument() {
        String out = lang.ktav.pure.PureKtav.dumps(sampleDoc());
        Value back = lang.ktav.pure.PureKtav.loads(out);
        assertTrue(CorpusHarness.valueEquals(sampleDoc(), back),
                "dumps -> loads must preserve the constructed tree: " + out);
    }

    @Test
    void toStringForceStringsFlattensEveryLeaf() {
        String out = lang.ktav.pure.PureKtav.toStringForceStrings(sampleDoc());
        Value.Obj back = assertInstanceOf(Value.Obj.class, lang.ktav.pure.PureKtav.loads(out));
        for (String key : List.of("name", "count", "ratio", "flag", "nothing")) {
            assertInstanceOf(Value.Str.class, back.entries().get(key),
                    "leaf " + key + " must coerce to String: " + out);
        }
        assertEquals("42", ((Value.Str) back.entries().get("count")).value());
        assertEquals("true", ((Value.Str) back.entries().get("flag")).value());
        assertEquals("null", ((Value.Str) back.entries().get("nothing")).value());
        // Raw marker for lexically-numeric text, per § 5.9.5.
        LinkedHashMap<String, Value> m = new LinkedHashMap<>();
        m.put("g", new Value.Flt("1e400"));
        assertTrue(lang.ktav.pure.PureKtav.toStringForceStrings(new Value.Obj(m))
                .contains("g:: 1e400"));
    }

    @Test
    void emitCanonicalFloatShapesFollowSection5_9_8() {
        // Scientific iff abs >= 1e7 or abs < 1e-2 (pinned by the corpus
        // oracles valid/numbers/float/notation_boundaries.canonical).
        assertEquals("a: 1e7\n",
                lang.ktav.pure.PureKtav.canonicalFromSource("a: 1e7\n"));
        assertEquals("a: 0.01\n",
                lang.ktav.pure.PureKtav.canonicalFromSource("a: 0.01\n"));
        assertEquals("a: 9999999.0\n",
                lang.ktav.pure.PureKtav.canonicalFromSource("a: 9999999.0\n"));
        assertEquals("a: 1e-3\n",
                lang.ktav.pure.PureKtav.canonicalFromSource("a: 1e-3\n"));
        assertEquals("a: 1.5\n",
                lang.ktav.pure.PureKtav.canonicalFromSource("a: 1.50\n"));
        // § 5.9.8: whole values in plain range keep a forced ".0" — the
        // shortest round-trip of 5e3 is 5000, rendered "5000.0".
        assertEquals("a: 5000.0\n",
                lang.ktav.pure.PureKtav.canonicalFromSource("a: 5e3\n"));
    }

    @Test
    void canonicalPreservesInsertionOrderNeverSorts() {
        String out = lang.ktav.pure.PureKtav.canonicalFromSource("z: 1\na: 2\nm: 3\n");
        assertTrue(out.indexOf("z:") < out.indexOf("a:") && out.indexOf("a:") < out.indexOf("m:"),
                "insertion order must be preserved, never convenience-sorted: " + out);
        assertEquals(out, lang.ktav.pure.PureKtav.canonicalFromSource(out),
                "canonical must be a fixed point");
    }

    @Test
    void emptyRootShapes() {
        // § 5.9.3: empty Object root = zero bytes; empty Array root =
        // the single line "[]" followed by LF (else it would re-parse
        // as an empty Object root).
        assertEquals("", lang.ktav.pure.PureKtav.canonicalFromSource("{}"));
        assertEquals("[]\n",
                lang.ktav.pure.PureKtav.canonicalFromSource("[]"));
    }

    // ── Exact canonical text: iterative emission hand-trace pins ────────

    private static Value.Obj objOf(Object... kv) {
        LinkedHashMap<String, Value> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Value) kv[i + 1]);
        }
        return new Value.Obj(m);
    }

    @Test
    void canonicalExactNestedObjectArraySiblings() {
        // Shape 1: object root with an object pair, an array pair
        // holding a nested array, and a trailing scalar sibling.
        Value.Obj root = objOf(
                "a", Value.Int.of(1),
                "n", objOf("m", Value.Int.of(2)),
                "arr", new Value.Arr(List.of(Value.Int.of(1),
                        new Value.Arr(List.of(Value.Int.of(2))))),
                "z", new Value.Str("s"));
        assertEquals("a: 1\n"
                + "n: {\n"
                + "    m: 2\n"
                + "}\n"
                + "arr: [\n"
                + "    1\n"
                + "    [\n"
                + "        2\n"
                + "    ]\n"
                + "]\n"
                + "z: s\n",
                lang.ktav.pure.PureKtav.emitCanonical(root));
    }

    @Test
    void canonicalExactWrappedRootArrayCompoundFirst() {
        // Shape 2: root Array whose first item is an Object → wrapped;
        // the "]" closer sits at indent 0 (§ 5.9.3), items at indent 1.
        Value.Arr root = new Value.Arr(List.of(
                objOf("k", Value.Int.of(1)), Value.Int.of(1)));
        assertEquals("[\n"
                + "    {\n"
                + "        k: 1\n"
                + "    }\n"
                + "    1\n"
                + "]\n",
                lang.ktav.pure.PureKtav.emitCanonical(root));
    }

    @Test
    void canonicalExactUnwrappedRootArrayScalarFirst() {
        // Shape 3: root Array whose first item is a scalar → NOT
        // wrapped; only index 0 is root-exposed (§ 5.9.6).
        Value.Arr root = new Value.Arr(List.of(
                Value.Int.of(1), objOf("k", Value.Int.of(2))));
        assertEquals("1\n"
                + "{\n"
                + "    k: 2\n"
                + "}\n",
                lang.ktav.pure.PureKtav.emitCanonical(root));
    }

    @Test
    void canonicalExactEmptyCompounds() {
        // Shape 4: empty compounds inline in pairs; empty roots.
        assertEquals("a: {}\nb: []\n",
                lang.ktav.pure.PureKtav.emitCanonical(
                        objOf("a", new Value.Obj(new LinkedHashMap<>()),
                                "b", new Value.Arr(List.of()))));
        assertEquals("",
                lang.ktav.pure.PureKtav.emitCanonical(
                        new Value.Obj(new LinkedHashMap<>())));
        assertEquals("[]\n", lang.ktav.pure.PureKtav.emitCanonical(
                new Value.Arr(List.of())));
    }

    @Test
    void canonicalExactDeepAlternatingNesting() {
        // Alternating object/array sibling nesting four levels deep.
        Value.Obj root = objOf(
                "x", new Value.Arr(List.of(
                        objOf("y", new Value.Arr(List.of(
                                objOf("z", Value.Int.of(7))))))),
                "after", Value.Int.of(8));
        assertEquals("x: [\n"
                + "    {\n"
                + "        y: [\n"
                + "            {\n"
                + "                z: 7\n"
                + "            }\n"
                + "        ]\n"
                + "    }\n"
                + "]\n"
                + "after: 8\n",
                lang.ktav.pure.PureKtav.emitCanonical(root));
    }

    // ── Decoded-path regressions (POP_PATH audit pins) ──────────────────

    @Test
    void nonFinitePathDoesNotLeakCompoundSiblingKeys() {
        // The "a" pair is a compound (array body) whose key must be
        // popped by its queued POP_PATH BEFORE "b" emits — otherwise
        // the rejection path would wrongly be ["a", "b"].
        Value.Obj root = objOf(
                "a", new Value.Arr(List.of(Value.Int.of(1),
                        Value.Int.of(2))),
                "b", new Value.Flt("NaN"));
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.dumps(root));
        assertEquals("NonFiniteFloat", e.getReason());
        assertEquals(List.of("b"), e.getPath(),
                "the compound pair's key must not leak into later "
                        + "siblings' paths");
    }

    @Test
    void nonFinitePathIsFullNestedChain() {
        Value.Obj root = objOf("o", objOf("p", new Value.Flt("NaN")));
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.dumps(root));
        assertEquals("NonFiniteFloat", e.getReason());
        assertEquals(List.of("o", "p"), e.getPath());
    }

    @Test
    void formatIsFixedPointAndPreservesComments() {
        String src = "## header\nservice: web\n\n\n## nested\ndb.timeout: 30\n\n\n";
        String once = lang.ktav.pure.PureKtav.format(src);
        assertTrue(once.contains("## header"), "comment must survive: " + once);
        assertTrue(once.contains("## nested"), "nested comment must survive: " + once);
        assertTrue(!once.contains("\n\n\n"), "blank runs collapse to one: " + once);
        assertEquals(once, lang.ktav.pure.PureKtav.format(once), "fixed point");
    }

    // ── Formatter regressions (repair round 5: ordering / finish) ───────

    @Test
    void formatClosedExplicitArrayRootDoesNotCrash() {
        // closeFrame pops the root frame and empties the stack; finish()
        // must use the recorded completed root, never pop an empty stack.
        assertEquals("1\n", lang.ktav.pure.PureKtav.format("[\n1\n]"));
    }

    @Test
    void formatWrappedRootArrayEmitsBothBrackets() {
        // Matches the independent oracle
        // spec/versions/0.8/tests/valid/top_level_array/
        // empty_compound_first_item.canonical.ktav
        assertEquals("[\n    {}\n    2\n]\n",
                lang.ktav.pure.PureKtav.format("[\n{}\n2\n]"));
    }

    @Test
    void formatPreservesKeyOrderAndIsFixedPoint() {
        String src = "a: 1\nb: 2\n";
        assertEquals("a: 1\nb: 2\n", lang.ktav.pure.PureKtav.format(src));
        String once = lang.ktav.pure.PureKtav.format(src);
        assertEquals(once, lang.ktav.pure.PureKtav.format(once),
                "format must be a fixed point");
    }

    @Test
    void formatKeepsCommentBeforeNestedCloserInsideObject() {
        String src = "o: {\n    m: 2\n    ## c\n}\n";
        assertEquals(src, lang.ktav.pure.PureKtav.format(src),
                "a comment immediately before the closer stays inside "
                        + "the compound (emitted before '}')");
    }

    @Test
    void formatKeepsTrailingCommentOnRootObject() {
        String src = "a: 1\n## t\n";
        String once = lang.ktav.pure.PureKtav.format(src);
        assertEquals("a: 1\n## t\n", once);
        assertEquals(once, lang.ktav.pure.PureKtav.format(once),
                "fixed point with trailing root comment");
    }

    @Test
    void formatQuotesRootFirstKeyStartingWithBom() {
        // §5.9.10 rule (c): the first segment of the root Object's
        // first-serialized key must be quoted when its decoded content
        // begins with U+FEFF — bare form would put the raw UTF-8 BOM at
        // byte offset 0, where §3.1 strips it. The opening '"' occupies
        // byte 0 instead; U+FEFF itself needs no escape inside the
        // quoted segment. The second key is unaffected.
        String src = "\"\uFEFFkey\": 1\nk2: 2\n";
        String once = lang.ktav.pure.PureKtav.format(src);
        assertEquals("\"\uFEFFkey\": 1\nk2: 2\n", once,
                "root-first BOM key must be quoted, second key untouched");
        assertTrue(once.startsWith("\""),
                "quoted key must place '\"' at byte offset 0");
        assertEquals(once, lang.ktav.pure.PureKtav.format(once),
                "fixed point with root-first BOM key");
    }

    @Test
    void formatKeepsCommentsBeforeBothClosersInNestedMixedCompound() {
        // §3.4 / §5.9: trailing comments belong to the compound they
        // precede the closer of; a well-formed document formats to
        // itself with each comment emitted before its own closer.
        String src = "o: {\n    a: [\n        1\n"
                + "        ## inner\n    ]\n    ## outer\n}\n";
        assertEquals(src, lang.ktav.pure.PureKtav.format(src),
                "comments stay inside their compounds, before each closer");
        String once = lang.ktav.pure.PureKtav.format(src);
        assertEquals(once, lang.ktav.pure.PureKtav.format(once),
                "fixed point with comments before both closers");
    }

    // ── Writer rejections (spec § 5.9.0 reason codes) ───────────────────

    @Test
    void dumpsRejectsScalarRoot() {
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.dumps(new Value.Str("x")));
        assertEquals("UnrepresentableAt", e.getError());
        assertEquals("ScalarRoot", e.getReason());
        assertNotNull(e.getPath());
        assertTrue(e.getPath().isEmpty(), "ScalarRoot path must be empty");
        assertEquals("§5.9.0", e.getSpecSection());
    }

    @Test
    void dumpsRejectsNonFiniteFloatWithPath() {
        LinkedHashMap<String, Value> inner = new LinkedHashMap<>();
        inner.put("b", new Value.Flt("NaN"));
        LinkedHashMap<String, Value> outer = new LinkedHashMap<>();
        outer.put("a", new Value.Obj(inner));
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.dumps(new Value.Obj(outer)));
        assertEquals("NonFiniteFloat", e.getReason());
        assertEquals(List.of("a", "b"), e.getPath());
        assertEquals("§5.9.0", e.getSpecSection());
    }

    @Test
    void dumpsRejectsEmptyKeyNameBeforeItsBytes() {
        LinkedHashMap<String, Value> inner = new LinkedHashMap<>();
        inner.put("", new Value.Int("1"));
        LinkedHashMap<String, Value> outer = new LinkedHashMap<>();
        outer.put("a", new Value.Obj(inner));
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.dumps(new Value.Obj(outer)));
        assertEquals("EmptyKeyName", e.getReason(), "empty key name is normative: " + e);
        assertEquals(List.of("a", ""), e.getPath(),
                "path includes the offending pair's key");
    }

    // ── Parse-error envelope shape (distinct from corpus classes) ───────

    @Test
    void parseErrorCarriesStructuredEnvelope() {
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loads("a: 1\na: 2\n"));
        assertEquals("DuplicateKey", e.getError());
        assertEquals(List.of("a"), e.getPath());
        assertEquals(2L, e.getLine());
        assertEquals("a: 2", e.getLineText());
        assertNotNull(e.getSpanStart());
        assertNotNull(e.getSpanEnd());
        assertNull(e.getReason());
        assertNotNull(e.getMessage());
        assertTrue(!e.getMessage().isBlank());
    }

    @Test
    void strictErrorCarriesBodyCanonicalAndSpecSection() {
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loadsStrict("v: 1.10\n"));
        assertEquals("LossyScalar", e.getError());
        assertEquals("1.10", e.getBody());
        assertEquals("1.1", e.getCanonical());
        assertEquals("§3.6/§5.2", e.getSpecSection());
        assertEquals(1L, e.getLine());
        assertEquals("v: 1.10", e.getLineText());
    }

    @Test
    void integerTextIsCanonicalDecimal() {
        Value.Obj top = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads("n: -0\n"));
        // § 5.2: -0 normalizes to 0 in the i64 domain.
        assertEquals(new Value.Int("0"), top.entries().get("n"));
        assertEquals(new BigInteger("0"),
                assertInstanceOf(Value.Int.class, top.entries().get("n")).toBigInteger());
    }
}
