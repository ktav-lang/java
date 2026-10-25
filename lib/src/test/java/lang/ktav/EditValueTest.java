package lang.ktav;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verbatim port of the reference layout regressions
 * ({@code tests/suite/api/text/edit_value/layout.rs}, 22 named tests,
 * every tabular subcase, exact source/expected bytes). Every case runs
 * BOTH API forms (textual path + decoded segments) on BOTH backends;
 * the engine's own semantic verification covers the reference's
 * {@code parse(result) == wanted} assertion.
 */
final class EditValueTest {

    /** The reference {@code check}: both forms must yield exactly {@code expected}. */
    private static void check(Backend backend, String source,
            List<String> path, Value replacement, String expected,
            String name) {
        String textual = textual(path);
        assertEquals(expected, backend.editValue(source, textual, replacement),
                name + " [textual]");
        assertEquals(expected,
                backend.editValueSegments(source, path, replacement),
                name + " [segments]");
    }

    /** Escape each segment and join with '.', like the reference helper. */
    private static String textual(List<String> segments) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                out.append('.');
            }
            out.append(segments.get(i).replace("\\", "\\\\")
                    .replace(".", "\\."));
        }
        return out.toString();
    }

    private static Value s(String v) {
        return new Value.Str(v);
    }

    private static Value obj(Object... kv) {
        java.util.LinkedHashMap<String, Value> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Value) kv[i + 1]);
        }
        return new Value.Obj(m);
    }

    private static Value arr(Value... items) {
        return new Value.Arr(List.of(items));
    }

    // ── 1 ───────────────────────────────────────────────────────────────

    private static void deepLeafPreservesEachSourceForm(Backend b) {
        String name = "deep_leaf_preserves_each_source_form";
        for (String source : new String[] {
                "targets.storagehost.address: old\ntargets.storagehost.user: mcp-agent\n",
                "targets: {storagehost: {address: old, user: mcp-agent}}\n",
                "targets: {\n  storagehost: {\n    address: old\n    user: mcp-agent\n  }\n}\n"}) {
            check(b, source,
                    List.of("targets", "storagehost", "address"),
                    s("192.168.0.88:22"),
                    source.replace("old", "192.168.0.88:22"), name);
        }
    }

    // ── 2 ───────────────────────────────────────────────────────────────

    private static void deepArrayLeafPreservesMixedForms(Backend b) {
        String name = "deep_array_leaf_preserves_mixed_forms";
        for (String source : new String[] {
                "targets: [{storagehost: [old, keep]}, other]\n",
                "targets: [\n  {storagehost: [old, keep]}\n  other\n]\n",
                "targets: [\n  {\n    storagehost: [\n      old\n      keep\n    ]\n  }\n  other\n]\n"}) {
            check(b, source,
                    List.of("targets", "0", "storagehost", "0"),
                    s("new"), source.replace("old", "new"), name);
        }
    }

    // ── 3 ───────────────────────────────────────────────────────────────

    private static void wholeInlineContainersRemainInline(Backend b) {
        String name = "whole_inline_containers_remain_inline";
        Object[][] cases = {
                {"a: {x: 1}\n", obj("y", new Value.Int("2")), "a: {y: 2}\n"},
                {"a: [1, 2]\n", arr(new Value.Int("3"), new Value.Int("4")),
                        "a: [3, 4]\n"},
                {"a: {}\n", obj("y", new Value.Int("2")), "a: {y: 2}\n"},
                {"a: []\n", arr(new Value.Int("3"), new Value.Int("4")),
                        "a: [3, 4]\n"},
                {"a: {}\n", arr(new Value.Int("3"), new Value.Int("4")),
                        "a: [3, 4]\n"},
        };
        for (Object[] c : cases) {
            check(b, (String) c[0], List.of("a"), (Value) c[1],
                    (String) c[2], name);
        }
    }

    // ── 4 ───────────────────────────────────────────────────────────────

    private static void emptyingBlockContainersDoesNotCollapseThem(Backend b) {
        String name = "emptying_block_containers_does_not_collapse_them";
        check(b, "a: {\n  x: 1\n}\n", List.of("a"), obj(),
                "a: {\n}\n", name);
        check(b, "a: [\n  1\n]\n", List.of("a"), arr(),
                "a: [\n]\n", name);
    }

    // ── 5 ───────────────────────────────────────────────────────────────

    private static void newlineUnfoldsOnlyInlineAncestorChain(Backend b) {
        check(b,
                "## top\na: [{x: old, untouched: [1, 2]}, {keep: yes}]\nz: keep\n",
                List.of("a", "0", "x"), s("one\ntwo"),
                "## top\na: [\n    {\n        x: ((\none\ntwo\n        ))\n"
                        + "        untouched: [1, 2]\n    }\n    {keep: yes}\n]"
                        + "\nz: keep\n",
                "newline_unfolds_only_inline_ancestor_chain");
    }

    // ── 6 ───────────────────────────────────────────────────────────────

    private static void newlineUnderDottedKeyDoesNotExpandDottedAncestors(
            Backend b) {
        check(b,
                "a.b: [old, keep]\na.c: other\n",
                List.of("a", "b", "0"), s("one\ntwo"),
                "a.b: [\n    ((\none\ntwo\n    ))\n    keep\n]\na.c: other\n",
                "newline_under_dotted_key_does_not_expand_dotted_ancestors");
    }

    // ── 7 ───────────────────────────────────────────────────────────────

    private static void denseEscapesUnfoldButSparseEscapesDoNot(Backend b) {
        String name = "dense_escapes_unfold_but_sparse_escapes_do_not";
        check(b, "a: [old, keep]\n", List.of("a", "0"), s("a,b,c,d,e"),
                "a: [\n    a,b,c,d,e\n    keep\n]\n", name);
        check(b, "a: [old]\n", List.of("a", "0"), s("a,b,c,d"),
                "a: [a\\,b\\,c\\,d]\n", name);
        String sparse = "x".repeat(30) + "a,b,c,d,e";
        check(b, "a: [old]\n", List.of("a", "0"), s(sparse),
                "a: [" + sparse.replace(",", "\\,") + "]\n", name);
    }

    // ── 8 ───────────────────────────────────────────────────────────────

    private static void widthBoundaryIsInCharactersNotBytes(Backend b) {
        String name = "width_boundary_is_in_characters_not_bytes";
        for (Object[] b2 : new Object[][] {{95, false}, {96, true}}) {
            int size = (Integer) b2[0];
            boolean expanded = (Boolean) b2[1];
            String value = "я".repeat(size);
            String expected = expanded
                    ? "a: [\n    " + value + "\n]\n"
                    : "a: [" + value + "]\n";
            check(b, "a: [old]\n", List.of("a", "0"), s(value), expected, name);
        }
    }

    // ── 9 ───────────────────────────────────────────────────────────────

    private static void alreadyLongLineOnlyUnfoldsWhenItGrows(Backend b) {
        String name = "already_long_line_only_unfolds_when_it_grows";
        String source = "a: [" + "x".repeat(120) + ", keep]\n";
        check(b, source, List.of("a", "0"), s("y".repeat(120)),
                source.replace("x", "y"), name);
        check(b, source, List.of("a", "0"), s("y".repeat(121)),
                "a: [\n    " + "y".repeat(121) + "\n    keep\n]\n", name);
    }

    // ── 10 ──────────────────────────────────────────────────────────────

    private static void widthExpansionStopsWhenInnerLineFits(Backend b) {
        String value = "x".repeat(91);
        check(b, "a: [{x: old}]\n", List.of("a", "0", "x"), s(value),
                "a: [\n    {x: " + value + "}\n]\n",
                "width_expansion_stops_when_inner_line_fits");
    }

    // ── 11 ──────────────────────────────────────────────────────────────

    private static void escapeOverheadBoundaryIsExact(Backend b) {
        String name = "escape_overhead_boundary_is_exact";
        String dense = "x".repeat(7) + "a,b,c,d,e";
        String sparse = "x".repeat(8) + "a,b,c,d,e";
        check(b, "a: [old]\n", List.of("a", "0"), s(dense),
                "a: [\n    " + dense + "\n]\n", name);
        check(b, "a: [old]\n", List.of("a", "0"), s(sparse),
                "a: [" + sparse.replace(",", "\\,") + "]\n", name);
    }

    // ── 12 ──────────────────────────────────────────────────────────────

    private static void longChangedLeafDoesNotUnfoldUnrelatedNestedBranches(
            Backend b) {
        String value = "x".repeat(100);
        check(b, "a: [old, {untouched: [1, 2]}]\n", List.of("a", "0"),
                s(value),
                "a: [\n    " + value + "\n    {untouched: [1, 2]}\n]\n",
                "long_changed_leaf_does_not_unfold_unrelated_nested_branches");
    }

    // ── 13 ──────────────────────────────────────────────────────────────

    private static void promotedSiblingsKeepValuesWhenInlineEscapesLoseMeaning(
            Backend b) {
        check(b,
                "a: [old, a\\,b, \\u00342, :: literal]\n",
                List.of("a", "0"), s("x\ny"),
                "a: [\n    ((\nx\ny\n    ))\n    a,b\n    :: 42\n"
                        + "    :: :: literal\n]\n",
                "promoted_siblings_keep_values_when_inline_escapes_lose_meaning");
    }

    // ── 14 ──────────────────────────────────────────────────────────────

    private static void promotedPairsKeepKeySpellingAndRawMarkers(Backend b) {
        check(b,
                "a: {\"x.y\": old, nested.key::  42, n: 0007}\n",
                List.of("a", "x.y"), s("x\ny"),
                "a: {\n    \"x.y\": ((\nx\ny\n    ))\n"
                        + "    nested.key::  42\n    n: 0007\n}\n",
                "promoted_pairs_keep_key_spelling_and_raw_markers");
    }

    // ── 15 ──────────────────────────────────────────────────────────────

    private static void rootInlinePromotionPreservesBomAndEof(Backend b) {
        check(b, "\uFEFF[old, keep]", List.of("0"), s("x\ny"),
                "\uFEFF[\n    ((\nx\ny\n    ))\n    keep\n]",
                "root_inline_promotion_preserves_bom_and_eof");
    }

    // ── 16 ──────────────────────────────────────────────────────────────

    private static void promotionPreservesEolCommentsAndNoFinalTerminator(
            Backend b) {
        String name = "promotion_preserves_eol_comments_and_no_final_terminator";
        for (String eol : new String[] {"\n", "\r", "\r\n"}) {
            String source = "## header" + eol + "a: [old, keep]";
            String expected = "## header" + eol + "a: [" + eol + "    (("
                    + eol + "x" + eol + "y" + eol + "    ))" + eol
                    + "    keep" + eol + "]";
            check(b, source, List.of("a", "0"), s("x\ny"), expected, name);
        }
    }

    // ── 17 ──────────────────────────────────────────────────────────────

    private static void commentsOutsidePromotedInlineValueRemainByteExact(
            Backend b) {
        check(b,
                "list: [\n  ## before\n  {x: old}\n  ## after\n]\n",
                List.of("list", "0", "x"), s("x\ny"),
                "list: [\n  ## before\n  {\n      x: ((\nx\ny\n      ))\n"
                        + "  }\n  ## after\n]\n",
                "comments_outside_promoted_inline_value_remain_byte_exact");
    }

    // ── 18 ──────────────────────────────────────────────────────────────

    private static void noopIgnoresAllReadabilityThresholds(Backend b) {
        String name = "noop_ignores_all_readability_thresholds";
        String source = "a: [" + "x".repeat(140) + ", a\\,b\\,c\\,d\\,e]\n";
        check(b, source, List.of("a", "0"), s("x".repeat(140)), source, name);
        check(b, source, List.of("a", "1"), s("a,b,c,d,e"), source, name);
    }

    // ── 19 ──────────────────────────────────────────────────────────────

    private static void typeChangeDoesNotForceUnrelatedLayoutChanges(
            Backend b) {
        String name = "type_change_does_not_force_unrelated_layout_changes";
        check(b, "a: {x: [1], keep: [2]}\n", List.of("a", "x"),
                Value.Bool.TRUE, "a: {x: true, keep: [2]}\n", name);
        check(b, "a: [1]\n", List.of("a"), s("42"), "a:: 42\n", name);
    }

    // ── 20 ──────────────────────────────────────────────────────────────

    private static void reopenedInlineParentPromotesOnlyItsPhysicalDefinition(
            Backend b) {
        check(b,
                "a: {x: old}\n## gap\na.y: keep\n",
                List.of("a", "x"), s("x\ny"),
                "a: {\n    x: ((\nx\ny\n    ))\n}\n## gap\na.y: keep\n",
                "reopened_inline_parent_promotes_only_its_physical_definition");
    }

    // ── 21 ──────────────────────────────────────────────────────────────

    private static void impossibleBlockEncodingKeepsValidInlineForm(Backend b) {
        String name = "impossible_block_encoding_keeps_valid_inline_form";
        for (String value : new String[] {"a\rb", "a\n))\nb"}) {
            String encoded = value.replace("\r", "\\r").replace("\n", "\\n");
            check(b, "a: [old]\n", List.of("a", "0"), s(value),
                    "a: [" + encoded + "]\n", name);
        }
        check(b, "a: [old, a\\rb]\n", List.of("a", "0"), s("x\ny"),
                "a: [x\\ny, a\\rb]\n", name);
    }

    // ── 22 ──────────────────────────────────────────────────────────────

    private static void unsafeCommentLossStillFailsClosed(Backend b) {
        String name = "unsafe_comment_loss_still_fails_closed";
        for (String source : new String[] {
                "a: {\n ## keep\n x: 1\n}\n",
                "a: [\n ## keep\n 1\n]\n"}) {
            KtavException e = assertThrows(KtavException.class,
                    () -> b.editValueSegments(source, List.of("a"),
                            Value.Null.NULL),
                    name + " " + source);
            assertEquals("EditUnsupported", e.getError(), name + " " + source);
        }
    }

    // ── runners ─────────────────────────────────────────────────────────

    private static final String[] NAMES = {
            "deep_leaf_preserves_each_source_form",
            "deep_array_leaf_preserves_mixed_forms",
            "whole_inline_containers_remain_inline",
            "emptying_block_containers_does_not_collapse_them",
            "newline_unfolds_only_inline_ancestor_chain",
            "newline_under_dotted_key_does_not_expand_dotted_ancestors",
            "dense_escapes_unfold_but_sparse_escapes_do_not",
            "width_boundary_is_in_characters_not_bytes",
            "already_long_line_only_unfolds_when_it_grows",
            "width_expansion_stops_when_inner_line_fits",
            "escape_overhead_boundary_is_exact",
            "long_changed_leaf_does_not_unfold_unrelated_nested_branches",
            "promoted_siblings_keep_values_when_inline_escapes_lose_meaning",
            "promoted_pairs_keep_key_spelling_and_raw_markers",
            "root_inline_promotion_preserves_bom_and_eof",
            "promotion_preserves_eol_comments_and_no_final_terminator",
            "comments_outside_promoted_inline_value_remain_byte_exact",
            "noop_ignores_all_readability_thresholds",
            "type_change_does_not_force_unrelated_layout_changes",
            "reopened_inline_parent_promotes_only_its_physical_definition",
            "impossible_block_encoding_keeps_valid_inline_form",
            "unsafe_comment_loss_still_fails_closed",
    };

    private static void runCase(Backend b, String name) {
        switch (name) {
            case "deep_leaf_preserves_each_source_form" ->
                    deepLeafPreservesEachSourceForm(b);
            case "deep_array_leaf_preserves_mixed_forms" ->
                    deepArrayLeafPreservesMixedForms(b);
            case "whole_inline_containers_remain_inline" ->
                    wholeInlineContainersRemainInline(b);
            case "emptying_block_containers_does_not_collapse_them" ->
                    emptyingBlockContainersDoesNotCollapseThem(b);
            case "newline_unfolds_only_inline_ancestor_chain" ->
                    newlineUnfoldsOnlyInlineAncestorChain(b);
            case "newline_under_dotted_key_does_not_expand_dotted_ancestors" ->
                    newlineUnderDottedKeyDoesNotExpandDottedAncestors(b);
            case "dense_escapes_unfold_but_sparse_escapes_do_not" ->
                    denseEscapesUnfoldButSparseEscapesDoNot(b);
            case "width_boundary_is_in_characters_not_bytes" ->
                    widthBoundaryIsInCharactersNotBytes(b);
            case "already_long_line_only_unfolds_when_it_grows" ->
                    alreadyLongLineOnlyUnfoldsWhenItGrows(b);
            case "width_expansion_stops_when_inner_line_fits" ->
                    widthExpansionStopsWhenInnerLineFits(b);
            case "escape_overhead_boundary_is_exact" ->
                    escapeOverheadBoundaryIsExact(b);
            case "long_changed_leaf_does_not_unfold_unrelated_nested_branches" ->
                    longChangedLeafDoesNotUnfoldUnrelatedNestedBranches(b);
            case "promoted_siblings_keep_values_when_inline_escapes_lose_meaning" ->
                    promotedSiblingsKeepValuesWhenInlineEscapesLoseMeaning(b);
            case "promoted_pairs_keep_key_spelling_and_raw_markers" ->
                    promotedPairsKeepKeySpellingAndRawMarkers(b);
            case "root_inline_promotion_preserves_bom_and_eof" ->
                    rootInlinePromotionPreservesBomAndEof(b);
            case "promotion_preserves_eol_comments_and_no_final_terminator" ->
                    promotionPreservesEolCommentsAndNoFinalTerminator(b);
            case "comments_outside_promoted_inline_value_remain_byte_exact" ->
                    commentsOutsidePromotedInlineValueRemainByteExact(b);
            case "noop_ignores_all_readability_thresholds" ->
                    noopIgnoresAllReadabilityThresholds(b);
            case "type_change_does_not_force_unrelated_layout_changes" ->
                    typeChangeDoesNotForceUnrelatedLayoutChanges(b);
            case "reopened_inline_parent_promotes_only_its_physical_definition" ->
                    reopenedInlineParentPromotesOnlyItsPhysicalDefinition(b);
            case "impossible_block_encoding_keeps_valid_inline_form" ->
                    impossibleBlockEncodingKeepsValidInlineForm(b);
            case "unsafe_comment_loss_still_fails_closed" ->
                    unsafeCommentLossStillFailsClosed(b);
            default -> throw new AssertionError("unknown case " + name);
        }
    }

    @TestFactory
    Stream<DynamicTest> nativeEdits() {
        Backend backend = Backend.nativeBackend();
        CorpusHarness.requireCabiFor(backend);
        return factor(backend);
    }

    @TestFactory
    Stream<DynamicTest> pure() {
        return factor(Backend.pureBackend());
    }

    private static Stream<DynamicTest> factor(Backend backend) {
        return java.util.Arrays.stream(NAMES)
                .map(n -> DynamicTest.dynamicTest(backend.name() + ": " + n,
                        () -> runCase(backend, n)));
    }

    // ── numeric wire parity (canonical_integer / canonical_float) ───────

    private static void runNumericParity(Backend b) {
        // Non-canonical but VALID numeric tags are normalized exactly
        // like the C ABI's wire decode, so the edit is a byte-identical
        // no-op — not a change, not an Unsupported.
        check(b, "n: 1\n", List.of("n"), new Value.Int("+0001"), "n: 1\n",
                "int plus/leading-zeros no-op");
        check(b, "n: 1.5\n", List.of("n"), new Value.Flt("1.50"), "n: 1.5\n",
                "float trailing-zeros no-op");
        check(b, "n: 0\n", List.of("n"), new Value.Int("-0"), "n: 0\n",
                "int signed zero no-op");
        check(b, "n: -0.0\n", List.of("n"), new Value.Flt("-0.0"), "n: -0.0\n",
                "float signed zero no-op");
        // Arbitrary precision is preserved on the WIRE (never i64-clamped),
        // but the SOURCE parser's i64 domain routes a 30-digit bare literal
        // to String — so a typed Integer over it can only fail closed:
        // EditUnsupported (semantic guard), NOT Message, NOT a silent
        // String-typed write. This is the proof the wire was not truncated.
        String huge = "9".repeat(30);
        KtavException hugeNoop = assertThrows(KtavException.class,
                () -> b.editValueSegments("n: " + huge + "\n", List.of("n"),
                        new Value.Int(huge)),
                "30-digit typed Integer over a String literal");
        assertEquals("EditUnsupported", hugeNoop.getError(),
                "wire keeps arbitrary digits; the semantic guard fails closed");
        KtavException hugeTextual = assertThrows(KtavException.class,
                () -> b.editValue("n: " + huge + "\n", "n", new Value.Int(huge)),
                "30-digit typed Integer over a String literal [textual]");
        assertEquals("EditUnsupported", hugeTextual.getError());
        // Positive precision control: > 2^53 but within i64 — the value IS
        // a typed Integer in the source, so it round-trips exactly.
        String big = "9007199254740993";
        check(b, "n: " + big + "\n", List.of("n"), new Value.Int(big),
                "n: " + big + "\n", "bigint no-op (>2^53, within i64)");
        check(b, "n: 1\n", List.of("n"), new Value.Int(big),
                "n: " + big + "\n", "bigint change");
        // Malformed payloads are wire-transport Message errors, never
        // Edit* and never an invented source location.
        for (Value bad : new Value[] {
                new Value.Int("12a3"), new Value.Int("+"), new Value.Int(""),
                new Value.Flt("abc"), new Value.Flt("12")}) {
            KtavException e = assertThrows(KtavException.class,
                    () -> b.editValueSegments("n: 1\n", List.of("n"), bad),
                    "malformed numeric payload " + bad);
            assertEquals("Message", e.getError(),
                    "malformed numeric payload " + bad);
            assertNull(e.getSpecSection(),
                    "Message carries no invented spec section");
        }
        // Non-finite wire floats stay verbatim and fail closed downstream
        // (the writers refuse them) — Unsupported, not a silent write.
        KtavException inf = assertThrows(KtavException.class,
                () -> b.editValueSegments("n: 1.0\n", List.of("n"),
                        new Value.Flt("1e400")),
                "non-finite float payload");
        assertEquals("EditUnsupported", inf.getError(),
                "non-finite float payload fails closed");
    }

    @TestFactory
    Stream<DynamicTest> numericParityNative() {
        Backend backend = Backend.nativeBackend();
        CorpusHarness.requireCabiFor(backend);
        return Stream.of(DynamicTest.dynamicTest("native: numeric wire parity",
                () -> runNumericParity(backend)));
    }

    @TestFactory
    Stream<DynamicTest> numericParityPure() {
        Backend backend = Backend.pureBackend();
        return Stream.of(DynamicTest.dynamicTest("pure: numeric wire parity",
                () -> runNumericParity(backend)));
    }

    // ── source-first contract over raw bytes ────────────────────────────

    /**
     * Source-first contract over RAW bytes: an invalid-UTF-8 source must
     * report the InvalidUtf8 parse envelope even when the path argument
     * is also malformed, and even with an empty segments list — never
     * the transport-level {@code EditInvalidPath} / {@code
     * EditNotFound}.
     */
    private static void runSourceFirstRawBytes(byte[] damaged) {
        byte[] bad = java.util.Arrays.copyOf(damaged, damaged.length);
        KtavException badPath = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.editValue(bad, "a\\q",
                        new Value.Int("1")));
        assertEquals("InvalidUtf8", badPath.getError(),
                "UTF-8 validation precedes the path decode");
        KtavException emptySegs = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.editValueSegments(bad,
                        List.of(), new Value.Int("1")));
        assertEquals("InvalidUtf8", emptySegs.getError(),
                "UTF-8 validation precedes the empty-segments NotFound");
    }

    @Test
    void pureSourceFirstOverRawBytes() {
        byte[] damaged = "k: 1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] raw = java.util.Arrays.copyOf(damaged, damaged.length + 1);
        raw[raw.length - 1] = (byte) 0xFF;
        runSourceFirstRawBytes(raw);
    }

    @Test
    void nativeSourceFirstOverRawBytes() {
        org.junit.jupiter.api.Assumptions.assumeTrue(TestPaths.cabiBuilt(),
                CorpusHarness.CABI_MISSING + " (" + TestPaths.CABI + ")");
        byte[] damaged = "k: 1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] raw = java.util.Arrays.copyOf(damaged, damaged.length + 1);
        raw[raw.length - 1] = (byte) 0xFF;
        KtavException e = assertThrows(KtavException.class,
                () -> Ktav.editValue(raw, "a\\q", new Value.Int("1")));
        assertEquals("InvalidUtf8", e.getError(),
                "the C ABI validates and parses the source first");
    }

    private EditValueTest() {
    }
}
