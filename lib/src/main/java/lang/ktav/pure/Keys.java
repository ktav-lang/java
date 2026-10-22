package lang.ktav.pure;

import java.util.ArrayList;
import java.util.List;

/**
 * Key-context scanning and validation (spec § 4, § 5.3, § 5.3.1,
 * § 5.3.3, § 6.4, § 6.5, § 6.16): the pair-separator scan, dotted-path
 * splitting, per-segment validation, and segment decoding.
 *
 * <p>Quoted segments ({@code "}, {@code '}, {@code `}) open only at a
 * segment-start position (the text's start, or after an unescaped dot,
 * plus edge whitespace); their content is opaque to colons, dots and
 * structural scanning, and is never trimmed.
 */
final class Keys {

    private Keys() {
    }

    /** Outcome of the pair-separator scan over one line's key region. */
    enum Colon {
        /** Not scanned yet / no separator. */
        ABSENT,
        /** Quoted segment opened at a segment start and never closed. */
        UNTERMINATED,
        FOUND
    }

    static final class ColonResult {
        final Colon kind;
        final int index; // byte offset of ':' when FOUND

        ColonResult(Colon kind, int index) {
            this.kind = kind;
            this.index = index;
        }
    }

    static boolean isQuote(byte b) {
        return b == '"' || b == '\'' || b == '`';
    }

    /**
     * End offset (exclusive) of the quoted segment opened at
     * {@code open} (the index of its own closing delimiter), or -1 if
     * it never closes before {@code end}. Backslash-escaped bytes are
     * skipped.
     */
    static int quotedSpanEnd(byte[] b, int open, int end) {
        byte quote = b[open];
        int j = open + 1;
        while (j < end) {
            if (b[j] == '\\') {
                j += 2;
                continue;
            }
            if (b[j] == quote) {
                return j;
            }
            j++;
        }
        return -1;
    }

    /**
     * § 4's separator-scanning rule: the first UNESCAPED {@code :}
     * outside quoted segments within {@code [from, end)}. Equivalent to
     * the Rust core's {@code scan_unescaped_colon} (segment-start
     * arming on unescaped dots, edge-whitespace skip before arming).
     */
    static ColonResult scanColon(byte[] b, int from, int end) {
        int i = from;
        boolean segStart = true;
        while (i < end) {
            if (segStart) {
                i = Bytes.skipWs(b, i, end);
                if (i < end && isQuote(b[i])) {
                    int close = quotedSpanEnd(b, i, end);
                    if (close < 0) {
                        return new ColonResult(Colon.UNTERMINATED, -1);
                    }
                    i = close + 1;
                    segStart = false;
                    continue;
                }
                segStart = false;
            }
            byte c = b[i];
            if (c == '\\') {
                i += 2; // escape lead consumes the next byte
            } else if (c == '.') {
                segStart = true;
                i++;
            } else if (c == ':') {
                return new ColonResult(Colon.FOUND, i);
            } else {
                i++;
            }
        }
        return new ColonResult(Colon.ABSENT, -1);
    }

    /**
     * Raw dotted-path segments of the (untrimmed) key region
     * {@code [from, end)}: split at unescaped dots, quoted segments
     * opaque. Mirrors the Rust {@code split_key_path}.
     */
    static List<int[]> splitPath(byte[] b, int from, int end) {
        List<int[]> out = new ArrayList<>();
        int start = from;
        int i = from;
        boolean segStart = true;
        while (i < end) {
            if (segStart) {
                i = Bytes.skipWs(b, i, end);
                if (i < end && isQuote(b[i])) {
                    int close = quotedSpanEnd(b, i, end);
                    if (close < 0) {
                        // Defensive: callers only reach here after the
                        // colon scan proved every quoted segment closed.
                        break;
                    }
                    i = close + 1;
                    segStart = false;
                    continue;
                }
                segStart = false;
            }
            byte c = b[i];
            if (c == '\\') {
                i += 2;
            } else if (c == '.') {
                out.add(new int[] { start, i });
                start = i + 1;
                i++;
                segStart = true;
            } else {
                i++;
            }
        }
        out.add(new int[] { start, end });
        return out;
    }

    // ------------------------------------------------------------------
    // Per-segment validation + decoding
    // ------------------------------------------------------------------

    /** One checked key segment: EMPTY, INVALID, or decoded content. */
    static final class Segment {
        static final int EMPTY = 0;   // EmptyKey (empty after trim / empty quoted)
        static final int INVALID = 1; // InvalidKey (forbidden byte / bad shape)
        static final int OK = 2;

        final int kind;
        final String decoded;
        final String rawPath; // raw (post-trim) spelling, for the InvalidKey path

        Segment(int kind, String decoded, String rawPath) {
            this.kind = kind;
            this.decoded = decoded;
            this.rawPath = rawPath;
        }
    }

    private static boolean forbiddenControl(byte b) {
        int c = b & 0xFF;
        return (c < 0x20 && c != 0x09 && c != 0x0B && c != 0x0C) || c == 0x7F;
    }

    private static boolean forbiddenBareByte(byte b) {
        return b == ',' || b == '{' || b == '}' || b == '[' || b == ']'
                || b == '(' || b == ')' || forbiddenControl(b);
    }

    /**
     * Byte length of the escape sequence at {@code i} ({@code b[i]}
     * must be {@code '\\'}): 2 for named forms, 6 for {@code \\uXXXX},
     * 12 for a surrogate pair, 1 for a dangling backslash, 2 fallback.
     * Sequence VALIDITY is the decoder's job.
     */
    private static int escapedLen(byte[] b, int i, int end) {
        if (i + 1 >= end) {
            return 1;
        }
        if (b[i + 1] != 'u' || i + 6 > end) {
            return 2;
        }
        if (!isHex(b[i + 2]) || !isHex(b[i + 3]) || !isHex(b[i + 4])
                || !isHex(b[i + 5])) {
            return 2;
        }
        int value = (hexVal(b[i + 2]) << 12) | (hexVal(b[i + 3]) << 8)
                | (hexVal(b[i + 4]) << 4) | hexVal(b[i + 5]);
        if (value < 0xD800 || value > 0xDBFF) {
            return 6;
        }
        if (i + 12 > end || b[i + 6] != '\\' || b[i + 7] != 'u'
                || !isHex(b[i + 8]) || !isHex(b[i + 9])
                || !isHex(b[i + 10]) || !isHex(b[i + 11])) {
            return 6;
        }
        int low = (hexVal(b[i + 8]) << 12) | (hexVal(b[i + 9]) << 8)
                | (hexVal(b[i + 10]) << 4) | hexVal(b[i + 11]);
        return (low >= 0xDC00 && low <= 0xDFFF) ? 12 : 6;
    }

    private static boolean isHex(byte b) {
        char c = (char) (b & 0xFF);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
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

    /**
     * Validate and decode one RAW (already outer-trimmed) key segment.
     * Validation happens on the raw text BEFORE decoding (§ 5.3.1):
     * empty after trim / empty quoted content → invalid-empty; a
     * forbidden raw byte, content after a quoted closer, or an
     * unterminated quoted segment → invalid; a malformed escape
     * surfaces at decode time as BadEscapeSequence.
     */
    static Segment checkAndDecode(byte[] b, int from, int end, int line,
            String lineText, int spanStart, int spanEnd) {
        int[] t = Bytes.trim(b, from, end);
        int s = t[0];
        int e = t[1];
        if (s == e) {
            return new Segment(Segment.EMPTY, "", "");
        }
        String rawPath = Bytes.text(b, s, e);
        if (isQuote(b[s])) {
            int closer = quotedSpanEnd(b, s, e);
            if (closer < 0 || closer != e - 1) {
                return new Segment(Segment.INVALID, "", rawPath);
            }
            for (int i = s + 1; i < closer; i++) {
                if (b[i] == '\\') {
                    i += escapedLen(b, i, e) - 1;
                    continue;
                }
                if (forbiddenControl(b[i])) {
                    return new Segment(Segment.INVALID, "", rawPath);
                }
            }
            if (closer == s + 1) {
                return new Segment(Segment.EMPTY, "", rawPath);
            }
            StringBuilder out = new StringBuilder(e - s);
            Escapes.decode(b, s + 1, closer, out, line, lineText,
                    spanStart, spanEnd);
            return new Segment(Segment.OK, out.toString(), rawPath);
        }
        // Bare segment.
        int i = s;
        while (i < e) {
            if (b[i] == '\\') {
                i += escapedLen(b, i, e);
                continue;
            }
            if (forbiddenBareByte(b[i])) {
                return new Segment(Segment.INVALID, "", rawPath);
            }
            i++;
        }
        StringBuilder out = new StringBuilder(e - s);
        Escapes.decode(b, s, e, out, line, lineText, spanStart, spanEnd);
        return new Segment(Segment.OK, out.toString(), rawPath);
    }

    /**
     * Decode a full key region into segments; throws EmptyKey /
     * InvalidKey / BadEscapeSequence. Returns decoded segments
     * (segment-count ≥ 1).
     */
    static List<String> parseKey(byte[] b, int from, int end, int line,
            String lineText, int spanStart, int spanEnd) {
        List<int[]> raw = splitPath(b, from, end);
        List<String> decoded = new ArrayList<>(raw.size());
        List<String> rawSpelling = new ArrayList<>(raw.size());
        // Per-segment order: the FIRST failing segment decides the
        // category (empty → EmptyKey, invalid → InvalidKey); a bad
        // escape already threw inside checkAndDecode.
        int defect = -1;
        for (int[] seg : raw) {
            Segment s = checkAndDecode(b, seg[0], seg[1], line, lineText,
                    spanStart, spanEnd);
            rawSpelling.add(s.rawPath);
            decoded.add(s.decoded);
            if (s.kind != Segment.OK) {
                defect = s.kind;
                break;
            }
        }
        if (defect == Segment.EMPTY) {
            throw SourceError.syntax("EmptyKey", "§6.5", line, lineText,
                    spanStart, spanEnd,
                    "EmptyKey at line " + line + ": empty key segment");
        }
        if (defect == Segment.INVALID) {
            throw SourceError.withPath("InvalidKey", "§6.4", line, lineText,
                    spanStart, spanEnd, rawSpelling,
                    "InvalidKey at line " + line + ": forbidden key byte");
        }
        return decoded;
    }
}
