package lang.ktav;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Byte-level POSITIVE and NEGATIVE controls for the § 3.3 whitespace
 * set (exactly 25 code points), exercised through the public
 * {@link PureKtav} API only (the pure classes are package-private).
 * Positive: every inline member trims identically around keys and
 * values, matched on exact UTF-8 bytes. Negative: adjacent
 * NON-whitespace code points that share lead bytes with whitespace
 * members (U+200B, U+1681, U+205E, U+3001) are preserved verbatim as
 * content.
 */
final class PureWhitespaceTest {

    /** All 25 § 3.3 whitespace code points (spec § 3.3, closed list). */
    private static final int[] WS = {
            0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x0085,
            0x00A0, 0x1680,
            0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
            0x2007, 0x2008, 0x2009, 0x200A,
            0x2028, 0x2029, 0x202F, 0x205F, 0x3000,
    };

    /** Non-whitespace neighbours sharing lead bytes with WS members. */
    private static final int[] NOT_WS = { 0x200B, 0x1681, 0x205E, 0x3001 };

    private static String cp(int c) {
        return new String(Character.toChars(c));
    }

    @Test
    void everyInlineWhitespaceMemberTrimsAroundKeysAndValues() {
        for (int c : WS) {
            // LF/CR are line terminators (§ 3.2), never inline padding.
            if (c == 0x000A || c == 0x000D) {
                continue;
            }
            String w = cp(c);
            String src = w + "k" + w + ": " + w + "v" + w + "\n";
            assertEquals(
                    lang.ktav.pure.PureKtav.loads("k: v\n"),
                    lang.ktav.pure.PureKtav.loads(src),
                    "U+" + Integer.toHexString(c) + " must trim");
            // The same document at the BYTE level: multi-byte members
            // are matched on their exact UTF-8 encodings.
            byte[] b = src.getBytes(StandardCharsets.UTF_8);
            assertEquals(
                    lang.ktav.pure.PureKtav.loads("k: v\n"),
                    lang.ktav.pure.PureKtav.loads(b),
                    "U+" + Integer.toHexString(c)
                            + " must trim at the byte level");
        }
    }

    @Test
    void adjacentNonWhitespaceCodePointsSurviveVerbatimAsContent() {
        for (int c : NOT_WS) {
            // A raw (::) String value is never trimmed or rewritten:
            // the code point survives byte-for-byte.
            String src = ":: a" + cp(c) + "b\n";
            Value parsed = lang.ktav.pure.PureKtav.loads(src);
            Value.Str item = (Value.Str) ((Value.Arr) parsed).items().get(0);
            assertEquals("a" + cp(c) + "b", item.value(),
                    "U+" + Integer.toHexString(c)
                            + " is NOT whitespace and must survive");
            // It is not separator whitespace either: a body consisting
            // of only that code point is that String, not an empty one.
            assertEquals(
                    lang.ktav.pure.PureKtav.loads("k:: " + cp(c) + "\n"),
                    lang.ktav.pure.PureKtav.loads("k: " + cp(c) + "\n"));
        }
    }

    @Test
    void wsEncodedExactlyPerSpecBytes() {
        // Byte-level spot checks of the exact UTF-8 encodings § 3.3
        // fixes: U+1680 = E1 9A 80, U+2000 = E2 80 80, U+2028 =
        // E2 80 A8, U+202F = E2 80 AF, U+205F = E2 81 9F,
        // U+3000 = E3 80 80.
        byte[] src = new byte[] {
                (byte) 0xE1, (byte) 0x9A, (byte) 0x80, // U+1680
                'k', (byte) 0xE2, (byte) 0x80, (byte) 0x80, // U+2000
                ':', ' ',
                (byte) 0xE2, (byte) 0x80, (byte) 0xA8, // U+2028
                'v', (byte) 0xE2, (byte) 0x80, (byte) 0xAF, // U+202F
                (byte) 0xE2, (byte) 0x81, (byte) 0x9F, // U+205F
                (byte) 0xE3, (byte) 0x80, (byte) 0x80, // U+3000
                '\n',
        };
        assertEquals(lang.ktav.pure.PureKtav.loads("k: v\n"),
                lang.ktav.pure.PureKtav.loads(src));
    }

    @Test
    void wrongEncodingIsNotWhitespace() {
        // E1 80 A0 decodes to U+1020 (SAMARITAN LETTER ALAF), which is
        // NOT in the § 3.3 set — a value built from those bytes must
        // survive as content.
        byte[] tricky = new byte[] {
                'k', ':', ':', ' ',
                (byte) 0xE1, (byte) 0x80, (byte) 0xA0, 'x', '\n',
        };
        // `k:: x` is a PAIR line (§ 5.0.1 rule 6: the `::` separator
        // with a non-empty key prefix before it), so the root is an
        // Object and the raw-marker String lives under key "k".
        Value parsed = lang.ktav.pure.PureKtav.loads(tricky);
        if (!(parsed instanceof Value.Obj obj)) {
            throw new AssertionError(
                    "'k:: …' is a pair line, root must be Value.Obj, got: " + parsed);
        }
        if (!(obj.entries().get("k") instanceof Value.Str item)) {
            throw new AssertionError(
                    "raw-marker pair value must be Value.Str, got: " + obj.entries().get("k"));
        }
        assertEquals("\u1020x", item.value());
    }
}
