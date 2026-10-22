package lang.ktav;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void unclosedExplicitRootSpansOpenerToEofWithoutALine() {
        KtavException e = err("a: [");
        assertEquals("UnclosedCompound", e.getError());
        assertEquals("§6.1", e.getSpecSection());
        assertNull(e.getLine(), "UnclosedCompound carries a span, not a line");
        assertEquals(3L, e.getSpanStart(), "span starts at the opener byte");
        assertEquals(4L, e.getSpanEnd(), "span ends at EOF");
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
        KtavException e = err("\"ab: 1\n");
        assertEquals("UnterminatedQuotedKey", e.getError());
        assertEquals("§6.16", e.getSpecSection());
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
        assertEquals(List.of("a"), e.getPath());
    }

    @Test
    void emptyKeySegmentIsRejected() {
        KtavException e = err("a..b: 1\n");
        assertEquals("EmptyKey", e.getError());
        assertEquals("§6.5", e.getSpecSection());
        assertEquals(1L, e.getLine());
    }

    @Test
    void missingSeparatorSpaceIsRejectedWithLineText() {
        KtavException e = err("a:1\n");
        assertEquals("MissingSeparatorSpace", e.getError());
        assertEquals("§6.10", e.getSpecSection());
        assertEquals(1L, e.getLine());
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
        assertEquals("a:1", err("a:1\r\n").getLineText());
    }

    // ── Depth bound ─────────────────────────────────────────────────────

    @Test
    void nestingBeyondTheBoundIsAnExplicitErrorNotAStackOverflow() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < lang.ktav.pure.PureParser.MAX_DEPTH + 10; i++) {
            deep.append("k: [\n");
        }
        KtavException e = err(deep.toString());
        assertEquals("Other", e.getError(),
                "depth exhaustion must be an explicit Other error");
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
        // lineText is the raw line DECODED as UTF-8; the invalid byte
        // becomes U+FFFD, and the text ends where the line ends.
        assertEquals("a: [1, \uFFFD]", e.getLineText());
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
        assertEquals("a: \uFFFD", e.getLineText(),
                "raw line decoded with replacement for the truncated bytes");
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
        // same as the native &str path.
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
