package lang.ktav.pure;

/**
 * § 3.7 escape sequences: the fourteen-entry table plus
 * {@code \\uXXXX} (§ 3.7.1 — exactly four hex digits, surrogate pairs
 * combined, lone surrogates rejected). A backslash at end-of-line is a
 * {@code BadEscapeSequence}. Used for keys and inline scalar values
 * alike; never for multi-line bodies, multi-line string content, or
 * comments.
 */
final class Escapes {

    private Escapes() {
    }

    private static final int SIMPLE = 0;
    private static final int UNICODE = 1;
    private static final int PAIR = 2;

    /** One recognised escape: kind, decoded code point, total byte length. */
    private static final class Esc {
        final int kind;
        final int cp;
        final int len;

        Esc(int kind, int cp, int len) {
            this.kind = kind;
            this.cp = cp;
            this.len = len;
        }
    }

    /**
     * Decode every escape in {@code [from, end)}; appends to
     * {@code out} and returns the count of recognised escapes
     * encountered (the § 5.2 provenance flag: any recognised escape
     * forces String classification).
     *
     * @throws SourceError BadEscapeSequence on any unrecognised or
     *     malformed form; {@code seqStart}/{@code seqEnd} locate the
     *     sequence text for the error span
     */
    static int decode(byte[] b, int from, int end, StringBuilder out,
            int line, String lineText, int spanStart, int spanEnd) {
        int count = 0;
        int i = from;
        while (i < end) {
            if (b[i] != '\\') {
                int len = seqLen(b, i, end);
                out.append(Bytes.text(b, i, i + len));
                i += len;
                continue;
            }
            Esc esc = scan(b, i, end, line, lineText, spanStart, spanEnd);
            count++;
            out.appendCodePoint(esc.cp);
            i += esc.len;
        }
        return count;
    }

    /** Byte length of the UTF-8 code point at {@code i}. */
    private static int seqLen(byte[] b, int i, int end) {
        int b0 = b[i] & 0xFF;
        if (b0 < 0x80 || b0 >= 0xF8) {
            return 1;
        }
        int len = (b0 >= 0xF0) ? 4 : (b0 >= 0xE0) ? 3 : 2;
        return Math.min(len, end - i);
    }

    private static final String HEX = "0123456789abcdefABCDEF";

    private static boolean isHex(byte b) {
        return HEX.indexOf((char) (b & 0xFF)) >= 0;
    }

    private static int hexVal(byte b) {
        char c = (char) (b & 0xFF);
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        return c - 'A' + 10;
    }

    private static Esc scan(byte[] b, int i, int end, int line,
            String lineText, int spanStart, int spanEnd) {
        if (i + 1 >= end) {
            throw badEscape(line, lineText, spanStart, spanEnd,
                    "\\<end-of-line>");
        }
        byte next = b[i + 1];
        int simple = simpleEscape(next);
        if (simple >= 0) {
            return new Esc(SIMPLE, simple, 2);
        }
        if (next == 'u') {
            if (i + 6 > end || !isHex(b[i + 2]) || !isHex(b[i + 3])
                    || !isHex(b[i + 4]) || !isHex(b[i + 5])) {
                throw badEscape(line, lineText, spanStart, spanEnd,
                        renderUnicode(b, i, end));
            }
            int value = (hexVal(b[i + 2]) << 12) | (hexVal(b[i + 3]) << 8)
                    | (hexVal(b[i + 4]) << 4) | hexVal(b[i + 5]);
            if (value >= 0xD800 && value <= 0xDBFF) {
                // High surrogate must pair with an immediate U+XXXX low escape.
                if (i + 12 <= end && b[i + 6] == '\\' && b[i + 7] == 'u'
                        && isHex(b[i + 8]) && isHex(b[i + 9])
                        && isHex(b[i + 10]) && isHex(b[i + 11])) {
                    int low = (hexVal(b[i + 8]) << 12)
                            | (hexVal(b[i + 9]) << 8)
                            | (hexVal(b[i + 10]) << 4) | hexVal(b[i + 11]);
                    if (low >= 0xDC00 && low <= 0xDFFF) {
                        int combined = 0x10000
                                + (value - 0xD800) * 0x400 + (low - 0xDC00);
                        return new Esc(PAIR, combined, 12);
                    }
                }
                throw badEscape(line, lineText, spanStart, spanEnd,
                        renderUnicode(b, i, end));
            }
            if (value >= 0xDC00 && value <= 0xDFFF) {
                throw badEscape(line, lineText, spanStart, spanEnd,
                        renderUnicode(b, i, end));
            }
            return new Esc(UNICODE, value, 6);
        }
        // Unrecognised named form.
        String seq;
        if (next >= 0) {
            seq = "\\" + (char) next;
        } else {
            seq = String.format("\\<0x%02X>", next & 0xFF);
        }
        throw badEscape(line, lineText, spanStart, spanEnd, seq);
    }

    private static int simpleEscape(byte c) {
        switch (c) {
            case '\\': return '\\';
            case ',': return ',';
            case '}': return '}';
            case ']': return ']';
            case '{': return '{';
            case '[': return '[';
            case 'n': return '\n';
            case 'r': return '\r';
            case '.': return '.';
            case ':': return ':';
            case '"': return '"';
            case '\'': return '\'';
            case '`': return '`';
            default: return -1;
        }
    }

    private static SourceError badEscape(int line, String lineText,
            int spanStart, int spanEnd, String sequence) {
        return SourceError.syntax("BadEscapeSequence", "§6.13", line,
                lineText, spanStart, spanEnd,
                "BadEscapeSequence at line " + line + ": '" + sequence + "'");
    }

    /** {@code \\u} + up to four raw bytes, ASCII printable where possible. */
    private static String renderUnicode(byte[] b, int i, int end) {
        int stop = Math.min(i + 6, end);
        StringBuilder sb = new StringBuilder("\\u");
        for (int j = i + 2; j < stop; j++) {
            int c = b[j] & 0xFF;
            if (c < 0x80) {
                sb.append((char) c);
            } else {
                sb.append(String.format("<0x%02X>", c));
            }
        }
        return sb.toString();
    }

    /**
     * Quick scan used by structural scanners: validate the escape at
     * {@code i} (must be {@code '\\'}) without decoding, returning its
     * total byte length. Throws BadEscapeSequence on malformed forms.
     */
    static int validateAt(byte[] b, int i, int end, int line,
            String lineText, int spanStart, int spanEnd) {
        return scan(b, i, end, line, lineText, spanStart, spanEnd).len;
    }
}
