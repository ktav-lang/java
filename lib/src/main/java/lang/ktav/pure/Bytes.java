package lang.ktav.pure;

/**
 * Byte-level helpers: the § 3.3 fixed 25-code-point whitespace set
 * recognised directly on UTF-8 bytes (never a host primitive), range
 * trimming, and UTF-8 decoding of validated slices.
 */
final class Bytes {

    private Bytes() {
    }

    /** ASCII members of the § 3.3 set (fast path). */
    static boolean isAsciiWs(byte b) {
        return b == ' ' || b == '\t' || b == 0x0B || b == 0x0C;
    }

    /**
     * Length in bytes of the § 3.3 whitespace code point starting at
     * {@code i} (LF/CR excluded — callers work within pre-split lines),
     * or 0 if the code point at {@code i} is not whitespace. The
     * multi-byte members are matched by their exact UTF-8 encodings
     * (spec § 3.3, the closed 25-code-point list): U+0085 = C2 85,
     * U+00A0 = C2 A0, U+1680 = E1 9A 80, U+2000..U+200A =
     * E2 80 80..8A, U+202F = E2 80 AF, U+2028 = E2 80 A8,
     * U+2029 = E2 80 A9, U+205F = E2 81 9F, U+3000 = E3 80 80.
     */
    static int wsAt(byte[] b, int i, int end) {
        if (i >= end) {
            return 0;
        }
        byte c = b[i];
        if (isAsciiWs(c)) {
            return 1;
        }
        if (c == (byte) 0xC2 && i + 1 < end
                && (b[i + 1] == (byte) 0x85 || b[i + 1] == (byte) 0xA0)) {
            return 2; // U+0085, U+00A0
        }
        if (c == (byte) 0xE1 && i + 2 < end
                && (b[i + 1] & 0xFF) == 0x9A
                && (b[i + 2] & 0xFF) == 0x80) {
            return 3; // U+1680
        }
        if (c == (byte) 0xE2 && i + 2 < end && (b[i + 1] & 0xFF) == 0x80) {
            int c3 = b[i + 2] & 0xFF;
            if (c3 >= 0x80 && c3 <= 0x8A) {
                return 3; // U+2000..U+200A
            }
            if (c3 == 0xA8 || c3 == 0xA9 || c3 == 0xAF) {
                return 3; // U+2028, U+2029, U+202F
            }
        }
        if (c == (byte) 0xE2 && i + 2 < end
                && (b[i + 1] & 0xFF) == 0x81
                && (b[i + 2] & 0xFF) == 0x9F) {
            return 3; // U+205F
        }
        if (c == (byte) 0xE3 && i + 2 < end
                && (b[i + 1] & 0xFF) == 0x80 && (b[i + 2] & 0xFF) == 0x80) {
            return 3; // U+3000
        }
        return 0;
    }

    /** First index at or after {@code from} whose code point is not § 3.3 whitespace. */
    static int skipWs(byte[] b, int from, int end) {
        int i = from;
        while (i < end) {
            int len = wsAt(b, i, end);
            if (len == 0) {
                break;
            }
            i += len;
        }
        return i;
    }

    /** Last index (exclusive) of the trailing non-whitespace run within {@code [from, end)}. */
    static int trimEnd(byte[] b, int from, int end) {
        int e = end;
        while (e > from) {
            // Walk back one byte at a time; a whitespace code point's
            // continuation bytes are never themselves whitespace leads,
            // so re-testing the code point from its lead is safe.
            int lead = e - 1;
            while (lead > from && (b[lead] & 0xC0) == 0x80) {
                lead--;
            }
            if (wsAt(b, lead, end) > 0) {
                e = lead;
            } else {
                break;
            }
        }
        return e;
    }

    /** Trim a byte range of § 3.3 whitespace on both edges. */
    static int[] trim(byte[] b, int from, int end) {
        int s = skipWs(b, from, end);
        return new int[] { s, trimEnd(b, s, end) };
    }

    /** Decode a validated UTF-8 byte range. */
    static String text(byte[] b, int from, int end) {
        return new String(b, from, end - from,
                java.nio.charset.StandardCharsets.UTF_8);
    }

    static boolean startsWithAt(byte[] b, int i, int end, byte b0, byte b1) {
        return i + 1 < end && b[i] == b0 && b[i + 1] == b1;
    }
}
