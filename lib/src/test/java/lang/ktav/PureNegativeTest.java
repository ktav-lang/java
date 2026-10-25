package lang.ktav;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative controls for each major pure-parser state and boundary —
 * asserting EXACT error classes, reason codes, and (where the contract
 * pins them) byte spans. One input per parser defect class, so a
 * regression in any single state surfaces as a single red test.
 */
final class PureNegativeTest {

    private static KtavException err(String src) {
        try {
            lang.ktav.pure.PureKtav.loads(src);
        } catch (KtavException e) {
            return e;
        }
        throw new AssertionError("expected KtavException for: " + src);
    }

    // ── Compound structure ──────────────────────────────────────────────

    @Test
    void unclosedExplicitRootSpansOpenerToEofWithTheOpenerLine() {
        KtavException e = err("a: [");
        assertEquals("UnclosedCompound", e.getError());
        assertEquals("§6.1", e.getSpecSection());
        // 0.9 location contract: the span is opener..EOF, and the line
        // number AND line_text describe the opener (valid UTF-8 source,
        // so the opener line always decodes).
        assertEquals(1L, e.getLine(), "line is the opener's line");
        assertEquals("a: [", e.getLineText(), "line_text is the opener line");
        assertEquals(3L, e.getSpanStart(), "span starts at the opener byte");
        assertEquals(4L, e.getSpanEnd(), "span ends at EOF");
    }

    /**
     * 0.9 opener-line regressions: line counting is over raw § 3.2
     * terminators (LF, lone CR, CRLF — one EOL each) and the line_text
     * strips exactly one trailing CR; the BOM does not rebase offsets.
     */
    @Test
    void unclosedCompoundOpenerLineOverLfCrCrlfAndBom() {
        KtavException lf = err("k: 1\na: [");
        assertEquals(2L, lf.getLine(), "LF: opener on line 2");
        assertEquals("a: [", lf.getLineText());
        assertEquals(8L, lf.getSpanStart(), "opener is the '[' byte, not the line start");

        KtavException cr = err("k: 1\ra: [");
        assertEquals(2L, cr.getLine(), "lone CR: opener on line 2");
        assertEquals("a: [", cr.getLineText());
        assertEquals(8L, cr.getSpanStart());

        KtavException crlf = err("k: 1\r\na: [");
        assertEquals(2L, crlf.getLine(), "CRLF is ONE terminator");
        assertEquals("a: [", crlf.getLineText(),
                "line_text strips exactly one trailing CR");
        assertEquals(9L, crlf.getSpanStart());

        KtavException bom = err("\uFEFFk: 1\na: [");
        assertEquals(2L, bom.getLine());
        assertEquals("a: [", bom.getLineText());
        assertEquals(11L, bom.getSpanStart(),
                "BOM stays in the raw coordinates (3 + 8)");

        // Multiline opener: the span starts at the '(' byte itself
        // (0.9 diagnostics09), not at the opening pair's line start.
        KtavException ml = err("k: (\nx");
        assertEquals("UnclosedCompound", ml.getError());
        assertEquals(1L, ml.getLine());
        assertEquals("k: (", ml.getLineText());
        assertEquals(3L, ml.getSpanStart());
        assertEquals(6L, ml.getSpanEnd());
    }

    @Test
    void crossedCloserIsUnbalancedBracket() {
        KtavException e = err("a: [\n}\n");
        assertEquals("UnbalancedBracket", e.getError());
        assertEquals("§6.1", e.getSpecSection());
        assertEquals(2L, e.getLine());
    }

    @Test
    void bareCloseAtTopLevelIsUnbalancedBracket() {
        KtavException e = err("]\n");
        assertEquals("UnbalancedBracket", e.getError());
    }

    @Test
    void orphanLineAfterTopLevelInlineIsRejected() {
        KtavException e = err("{a: 1}\nstray\n");
        assertEquals("OrphanLineAfterTopLevelInline", e.getError());
        assertEquals("§6.14", e.getSpecSection());
        assertEquals(2L, e.getLine());
    }

    @Test
    void unterminatedQuotedKeyIsRejected() {
        KtavException e = err("anchor: ok\n\"ab: 1\n");
        assertEquals("UnterminatedQuotedKey", e.getError());
        assertEquals("§6.16", e.getSpecSection());
    }

    /**
     * § 5.3.2 / Rust {@code build/insert.rs insert_dotted} ordering: the
     * blocked scalar prefix `a` is a KeyPathConflict over the decoded
     * prefix BEFORE the later quoted segment's malformed `\q` escape can
     * surface as BadEscapeSequence. Path carries the raw quoted spelling
     * (envelope raw fallback, error/envelope.rs kind_path).
     */
    @Test
    void conflictOverBlockedPrefixPrecedesLaterBadEscape() {
        KtavException e = err("a: 1\na.\"\\q\": 2\n");
        assertEquals("KeyPathConflict", e.getError());
        assertEquals(List.of("a", "\"\\q\""), e.getPath());
    }

    /** Control: no blocker on the path — the escape defect still wins. */
    @Test
    void badEscapeStillReportedWithoutBlockedPrefix() {
        KtavException e = err("a: {}\na.\"\\q\": 2\n");
        assertEquals("BadEscapeSequence", e.getError());
    }

    /** Control: a VALID `\\q` (escaped backslash + q) parses on all four operations. */
    @Test
    void validEscapedBackslashKeyParsesAllOperations() {
        String src = "a: {}\na.\"\\\\q\": 2\n";
        // loads / loadsStrict: outer 'a' is an Obj whose single key is
        // the DECODED segment (one backslash + 'q'), not the raw quoted
        // spelling, and the value is Int 2.
        for (Value.Obj top : List.of(
                assertInstanceOf(Value.Obj.class,
                        lang.ktav.pure.PureKtav.loads(src)),
                assertInstanceOf(Value.Obj.class,
                        lang.ktav.pure.PureKtav.loadsStrict(src)))) {
            Value inner = top.entries().get("a");
            Value.Obj obj = assertInstanceOf(Value.Obj.class, inner);
            assertTrue(obj.entries().containsKey("\\q"),
                    "decoded segment must be one backslash + q, got: "
                            + obj.entries().keySet());
            assertFalse(obj.entries().containsKey("\"\\q\""),
                    "raw quoted spelling must not survive decoding");
            assertEquals("2",
                    assertInstanceOf(Value.Int.class,
                            obj.entries().get("\\q")).text());
        }
        Value expected = new Value.Obj(java.util.Map.of(
                "a", new Value.Obj(java.util.Map.of("\\q", new Value.Int("2")))));
        assertEquals(expected, lang.ktav.pure.PureKtav.loadsStrict(
                lang.ktav.pure.PureKtav.format(src)));
        assertEquals(expected, lang.ktav.pure.PureKtav.loadsStrict(
                lang.ktav.pure.PureKtav.canonicalFromSource(src)));
    }

    // ── § 5.3.2 path priority, all four operations, PURE backend ────────

    /** The four pure operations, each throwing exactly once on {@code src}. */
    private static KtavException pureErr(String src, int op) {
        try {
            switch (op) {
                case 0 -> lang.ktav.pure.PureKtav.loads(src);
                case 1 -> lang.ktav.pure.PureKtav.loadsStrict(src);
                case 2 -> lang.ktav.pure.PureKtav.format(src);
                default -> lang.ktav.pure.PureKtav.canonicalFromSource(src);
            }
        } catch (KtavException e) {
            return e;
        }
        throw new AssertionError("expected KtavException (op " + op + "): "
                + src);
    }

    private static final List<String> OP_NAMES =
            List.of("loads", "loadsStrict", "format", "canonicalFromSource");

    /**
     * Conflict vector: the blocked scalar prefix wins over the later
     * bad escape on ALL four operations, and the formatter (not just
     * the parser/Builders) carries the FULL raw-split path.
     */
    @Test
    void conflictVectorKeyPathConflictWithPathOnAllFourPureOperations() {
        String src = "a: 1\na.\"\\q\": 2\n";
        for (int op = 0; op < 4; op++) {
            KtavException e = pureErr(src, op);
            assertEquals("KeyPathConflict", e.getError(),
                    OP_NAMES.get(op));
            assertEquals("§6.3", e.getSpecSection(), OP_NAMES.get(op));
            assertEquals(List.of("a", "\"\\q\""), e.getPath(),
                    OP_NAMES.get(op));
        }
    }

    /** Control: no blocker — BadEscapeSequence (§ 6.13) on all four. */
    @Test
    void controlVectorBadEscapeSequenceOnAllFourPureOperations() {
        String src = "a: {}\na.\"\\q\": 2\n";
        for (int op = 0; op < 4; op++) {
            KtavException e = pureErr(src, op);
            assertEquals("BadEscapeSequence", e.getError(),
                    OP_NAMES.get(op));
            assertEquals("§6.13", e.getSpecSection(), OP_NAMES.get(op));
        }
    }

    // ── Keys ────────────────────────────────────────────────────────────

    @Test
    void duplicateKeyNamesTheDecodedPath() {
        KtavException e = err("a: 1\na: 2\n");
        assertEquals("DuplicateKey", e.getError());
        assertEquals("§6.2", e.getSpecSection());
        assertEquals(List.of("a"), e.getPath());
        assertEquals(2L, e.getLine());
        assertEquals("a: 2", e.getLineText());
    }

    @Test
    void keyPathConflictObjectThenScalar() {
        KtavException e = err("a.b: 1\na: 2\n");
        assertEquals("KeyPathConflict", e.getError());
        assertEquals("§6.3", e.getSpecSection());
        assertEquals(List.of("a"), e.getPath());
    }

    @Test
    void keyPathConflictScalarThenObject() {
        KtavException e = err("a: 1\na.b: 2\n");
        assertEquals("KeyPathConflict", e.getError());
        assertEquals("§6.3", e.getSpecSection());
        // FULL raw-split path (error/envelope.rs kind_path), not the
        // blocked prefix: both segments decode, so both appear.
        assertEquals(List.of("a", "b"), e.getPath());
    }

    @Test
    void emptyKeySegmentIsRejected() {
        KtavException e = err("a..b: 1\n");
        assertEquals("EmptyKey", e.getError());
        assertEquals("§6.5", e.getSpecSection());
        assertEquals(1L, e.getLine());
    }

    @Test
    void firstLinesWithoutPairShapeRemainStringArrayItems() {
        for (String body : List.of("a:1", "\"ab: 1")) {
            assertEquals(new Value.Arr(List.of(new Value.Str(body))),
                    lang.ktav.pure.PureKtav.loads(body + "\n"));
        }
    }

    @Test
    void missingSeparatorSpaceIsRejectedWithLineText() {
        KtavException e = err("anchor: ok\na:1\n");
        assertEquals("MissingSeparatorSpace", e.getError());
        assertEquals("§6.10", e.getSpecSection());
        assertEquals(2L, e.getLine());
        assertEquals("a:1", e.getLineText());
    }

    // ── Strings / escapes ───────────────────────────────────────────────

    @Test
    void badEscapeSequenceCarriesTheSequence() {
        KtavException e = err("\"a\\qb\": 1\n");
        assertEquals("BadEscapeSequence", e.getError());
        assertEquals("§6.13", e.getSpecSection());
        // The offending sequence is named in the diagnostic (the envelope
        // carries it via message, like the core's Display rendering).
        assertTrue(e.getMessage().contains("\\q"),
                "message must name the sequence: " + e.getMessage());
    }

    @Test
    void loneHighSurrogateEscapeIsRejected() {
        KtavException e = err("{k: \"\\uD800\"}\n");
        assertEquals("BadEscapeSequence", e.getError(),
                "a lone surrogate must never reach the Value tree");
    }

    @Test
    void loneLowSurrogateEscapeIsRejected() {
        KtavException e = err("{k: \"\\uDC00\"}\n");
        assertEquals("BadEscapeSequence", e.getError());
    }

    // ── BOM + line endings ──────────────────────────────────────────────

    @Test
    void exactlyOneLeadingBomIsSkippedAndSpansKeepOriginalRawOffsets() {
        // The BOM is skipped for the grammar, but spans stay in the
        // ORIGINAL raw coordinates (§ 3.1 / 0.8.1 errata): the opener
        // '[' sits at raw byte 6 (after the 3 BOM bytes), EOF is 7 —
        // proving the span is NOT rebased after the skip.
        KtavException e = err("\uFEFFa: [");
        assertEquals("UnclosedCompound", e.getError());
        assertEquals(6L, e.getSpanStart());
        assertEquals(7L, e.getSpanEnd());

        // And with the BOM, a valid document parses to the same tree.
        assertEquals(
                lang.ktav.pure.PureKtav.loads("a: 1\n"),
                lang.ktav.pure.PureKtav.loads("\uFEFFa: 1\n"));
    }

    @Test
    void crlfAndCrAndLfAreAllLineTerminators() {
        assertEquals(
                lang.ktav.pure.PureKtav.loads("a: 1\nb: 2\n"),
                lang.ktav.pure.PureKtav.loads("a: 1\r\nb: 2\r\n"));
        assertEquals(
                lang.ktav.pure.PureKtav.loads("a: 1\nb: 2\n"),
                lang.ktav.pure.PureKtav.loads("a: 1\rb: 2\r"));
        // lineText strips exactly one trailing CR (envelope convention).
        assertEquals("a:1", err("anchor: ok\r\na:1\r\n").getLineText());
    }

    // ── Depth bound ─────────────────────────────────────────────────────

    @Test
    void nestingBeyondTheBoundIsAnExplicitErrorNotAStackOverflow() {
        // Balanced chain: the root '[' explicit frame plus MAX_DEPTH-1
        // nested openers fill the stack to exactly MAX_DEPTH frames and
        // still parse; one MORE opener exceeds the bound (pushDepth
        // throws when stack.size()+1 > MAX_DEPTH) and must fail with an
        // explicit Other error BEFORE any closer line is reached.
        int atLimit = lang.ktav.pure.PureParser.MAX_DEPTH - 1;
        StringBuilder ok = new StringBuilder("[\n");
        for (int i = 0; i < atLimit; i++) {
            ok.append("[\n");
        }
        ok.append("1\n");
        for (int i = 0; i < atLimit; i++) {
            ok.append("]\n");
        }
        ok.append("]\n");
        Value v = lang.ktav.pure.PureKtav.loads(ok.toString());
        for (int i = 0; i <= atLimit; i++) {
            v = assertInstanceOf(Value.Arr.class, v).items().get(0);
        }
        assertEquals(new Value.Int("1"), v);

        StringBuilder over = new StringBuilder("[\n");
        for (int i = 0; i <= atLimit; i++) {
            over.append("[\n");
        }
        over.append("1\n");
        for (int i = 0; i <= atLimit; i++) {
            over.append("]\n");
        }
        over.append("]\n");
        assertEquals("Other", err(over.toString()).getError());
    }

    @Test
    void dottedPathLongerThanTheBoundIsAnExplicitError() {
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < lang.ktav.pure.PureParser.MAX_DEPTH + 1; i++) {
            if (i > 0) path.append('.');
            path.append("s").append(i);
        }
        KtavException e = err(path.append(": 1\n").toString());
        assertEquals("Other", e.getError());
    }

    // ── InvalidUtf8 over RAW bytes (byte[] entry only) ──────────────────

    @Test
    void invalidUtf8SpanCoversTheMalformedSequence() {
        // Built in ONE expression so the local is effectively final and
        // legal to capture in the assertThrows lambda below.
        final byte[] raw = malformedUtf8SpanBytes();

        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loads(raw));
        assertEquals("InvalidUtf8", e.getError());
        assertEquals("§6.15", e.getSpecSection());
        assertEquals(1L, e.getLine());
        // 0.9: line_text is NULL for invalid UTF-8 — the source is not
        // valid text, so no line may be decoded (a lossily decoded line
        // with U+FFFD would pretend the source was valid).
        assertNull(e.getLineText(), "line_text must be null for InvalidUtf8");
        assertEquals(7L, e.getSpanStart(), "span starts at the first invalid byte");
        assertEquals(8L, e.getSpanEnd());
    }

    /**
     * The raw bytes {@code "a: [1, " + 0xFF + "]"}, built in one helper
     * so callers can hold the result in a single effectively-final
     * local. Length 9: the invalid byte sits at offset 7 and {@code ]}
     * at offset 8.
     */
    private static byte[] malformedUtf8SpanBytes() {
        byte[] head = "a: [1, ".getBytes(StandardCharsets.UTF_8);
        byte[] raw = java.util.Arrays.copyOf(head, head.length + 2);
        raw[raw.length - 2] = (byte) 0xFF;
        raw[raw.length - 1] = ']';
        return raw;
    }

    @Test
    void truncatedMultibyteAtEofSpansToEof() {
        byte[] raw = new byte[] {'a', ':', ' ', (byte) 0xE2, (byte) 0x82};
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loads(raw));
        assertEquals("InvalidUtf8", e.getError());
        assertEquals(3L, e.getSpanStart(), "span starts at the truncated lead byte");
        assertEquals(5L, e.getSpanEnd(), "truncated sequence clamps at EOF");
        assertNull(e.getLineText(), "0.9: line_text null for InvalidUtf8");
    }

    @Test
    void invalidUtf8OnLaterLineIsCountedOverRawTerminators() {
        byte[] first = "a: 1\nb: 2\n".getBytes(StandardCharsets.UTF_8);
        byte[] raw = java.util.Arrays.copyOf(first, first.length + 1);
        raw[raw.length - 1] = (byte) 0xC0; // overlong-form lead byte
        KtavException e = assertThrows(KtavException.class,
                () -> lang.ktav.pure.PureKtav.loads(raw));
        assertEquals("InvalidUtf8", e.getError());
        assertEquals(3L, e.getLine(), "line counted over raw LF terminators");
        assertEquals(10L, e.getSpanStart(), "first byte after the last LF");
    }

    @Test
    void stringEntryCanNeverThrowInvalidUtf8() {
        // A Java String is always valid Unicode, so encoding to UTF-8
        // first makes InvalidUtf8 unreachable from the String entry —
        // the byte[] entry (Ktav.loads(byte[])) is where the native
        // backend performs that same UTF-8 validation.
        assertNotNull(lang.ktav.pure.PureKtav.loads("\uD83D\uDE00: é\n"));
    }

    // ── Strict mode ─────────────────────────────────────────────────────

    @Test
    void strictLossyFloatNamesBodyAndCanonical() {
        KtavException e = err2("v: 5e3\n");
        assertEquals("LossyScalar", e.getError());
        assertEquals("5e3", e.getBody());
        assertEquals("5000.0", e.getCanonical());
        assertEquals("§3.6/§5.2", e.getSpecSection());
        assertEquals(1L, e.getLine());
        assertEquals("v: 5e3", e.getLineText());
    }

    @Test
    void strictLossyNegativeZeroCanonicalizesToZero() {
        KtavException e = err2("v: -0\n");
        assertEquals("LossyScalar", e.getError());
        assertEquals("-0", e.getBody());
        assertEquals("0", e.getCanonical());
    }

    @Test
    void strictLossyHexadecimalNamesBodyAndCanonical() {
        KtavException e = err2("v: 0x1A2B\n");
        assertEquals("LossyScalar", e.getError());
        assertEquals("0x1A2B", e.getBody());
        assertEquals("6699", e.getCanonical());
    }

    @Test
    void laxModeAcceptsWhatStrictRejects() {
        Value.Obj top = assertInstanceOf(Value.Obj.class,
                lang.ktav.pure.PureKtav.loads("v: 5e3\n"));
        assertInstanceOf(Value.Flt.class, top.entries().get("v"));
    }

    private static KtavException err2(String src) {
        try {
            lang.ktav.pure.PureKtav.loadsStrict(src);
        } catch (KtavException e) {
            return e;
        }
        throw new AssertionError("expected strict KtavException for: " + src);
    }
}
