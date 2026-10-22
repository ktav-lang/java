package lang.ktav.pure;

/**
 * Strict UTF-8 validation over raw bytes (spec § 3.1, § 6.15, § 9.3).
 * Runs BEFORE any grammar processing: a document that fails this check
 * is never diagnosed with any other category (0.8.1 errata).
 *
 * <p>The validator rejects overlong encodings, UTF-16 surrogates encoded
 * as UTF-8, code points above U+10FFFF, truncated sequences at EOF, and
 * stray continuation bytes. On failure it reports the first invalid byte
 * offset and a NON-EMPTY span covering the malformed sequence (clamped
 * at EOF for truncated forms), plus the 1-based line number counted over
 * the raw § 3.2 terminators (each LF, lone CR, or CR LF is one line).
 */
final class Utf8 {

    private Utf8() {
    }

    /**
     * Validate {@code bytes}; throw {@link SourceError} with error
     * {@code InvalidUtf8} on the first malformed sequence.
     */
    static void validate(byte[] bytes) {
        int i = 0;
        int n = bytes.length;
        while (i < n) {
            int b0 = bytes[i] & 0xFF;
            int len; // expected sequence length
            if (b0 < 0x80) {
                i++;
                continue;
            } else if (b0 >= 0xC2 && b0 <= 0xDF) {
                len = 2;
            } else if (b0 >= 0xE0 && b0 <= 0xEF) {
                len = 3;
            } else if (b0 >= 0xF0 && b0 <= 0xF4) {
                len = 4;
            } else {
                fail(bytes, i, 1);
                return; // not reached
            }
            // Continuation bytes 0x80..0xBF, with first-byte restrictions:
            // E0: second byte >= 0xA0; ED: second byte <= 0x9F (no surrogates);
            // F0: second byte >= 0x90; F4: second byte <= 0x8F (max U+10FFFF).
            int min1 = 0x80;
            int max1 = 0xBF;
            if (b0 == 0xE0) {
                min1 = 0xA0;
            } else if (b0 == 0xED) {
                max1 = 0x9F;
            } else if (b0 == 0xF0) {
                min1 = 0x90;
            } else if (b0 == 0xF4) {
                max1 = 0x8F;
            }
            int end = Math.min(i + len, n);
            for (int j = i + 1; j < end; j++) {
                int bj = bytes[j] & 0xFF;
                int lo = (j == i + 1) ? min1 : 0x80;
                int hi = (j == i + 1) ? max1 : 0xBF;
                if (bj < lo || bj > hi) {
                    // The malformed region is the intended sequence,
                    // ending before the offending byte.
                    fail(bytes, i, Math.max(j - i, 1));
                    return;
                }
            }
            if (end < i + len) {
                // Truncated at EOF.
                fail(bytes, i, end - i);
                return;
            }
            // Ranges above already exclude overlong forms and encoded
            // surrogates (E0/ED/F0/F4 first-continuation bounds), and
            // cap the code point at U+10FFFF.
            i += len;
        }
    }

    private static void fail(byte[] bytes, int start, int len) {
        int end = Math.min(start + Math.max(len, 1), bytes.length);
        int line = lineAt(bytes, start);
        String lineText = lineTextAt(bytes, start);
        throw SourceError.invalidUtf8(line, lineText, start, end);
    }

    /** 1-based line containing byte offset {@code pos}, over raw § 3.2 terminators. */
    static int lineAt(byte[] bytes, int pos) {
        int line = 1;
        int i = 0;
        while (i < pos && i < bytes.length) {
            byte b = bytes[i];
            if (b == '\n') {
                line++;
                i++;
            } else if (b == '\r') {
                line++;
                i += (i + 1 < bytes.length && bytes[i + 1] == '\n') ? 2 : 1;
            } else {
                i++;
            }
        }
        return line;
    }

    /** Raw line containing {@code pos}, one trailing CR stripped (envelope convention). */
    static String lineTextAt(byte[] bytes, int pos) {
        int p = Math.min(pos, bytes.length);
        int start = p;
        while (start > 0 && bytes[start - 1] != '\n' && bytes[start - 1] != '\r') {
            start--;
        }
        int end = p;
        while (end < bytes.length && bytes[end] != '\n' && bytes[end] != '\r') {
            end++;
        }
        if (end > start && bytes[end - 1] == '\r') {
            end--;
        }
        return Bytes.text(bytes, start, end);
    }
}
