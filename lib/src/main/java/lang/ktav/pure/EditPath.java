package lang.ktav.pure;

import java.util.ArrayList;
import java.util.List;

/**
 * Textual edit-path parsing ({@code key1.key2.0.key4}): the separator
 * is {@code .}; inside a segment {@code \.} is a literal dot and
 * {@code \\} a literal backslash; any other escape (or a trailing lone
 * backslash, or an empty segment) is
 * {@link EditError.Code#INVALID_PATH} with the UTF-8 byte offset of the
 * defect inside the path argument. Mirrors the Rust core's
 * {@code edit::parse_path} byte-for-byte.
 */
final class EditPath {

    private EditPath() {
    }

    /** Decode a textual path into literal segments. */
    static List<String> parse(byte[] path) {
        if (path.length == 0) {
            throw EditError.invalidPath(0);
        }
        List<String> segments = new ArrayList<>();
        int segStart = 0;
        StringBuilder cur = null; // non-null after the first escape
        int i = 0;
        while (i < path.length) {
            byte c = path[i];
            if (c == '.') {
                if (segStart == i && cur == null) {
                    throw EditError.invalidPath(i);
                }
                pushSegment(segments, cur, path, segStart, i);
                cur = null;
                segStart = i + 1;
                i++;
            } else if (c == '\\') {
                if (i + 1 < path.length
                        && (path[i + 1] == '.' || path[i + 1] == '\\')) {
                    if (cur == null) {
                        cur = new StringBuilder(Bytes.text(path, segStart, i));
                    }
                    cur.append(path[i + 1] == '.' ? '.' : '\\');
                    i += 2;
                } else {
                    throw EditError.invalidPath(i);
                }
            } else {
                int len = utf8Len(c);
                if (cur != null) {
                    cur.append(Bytes.text(path, i, Math.min(i + len, path.length)));
                }
                i += len;
            }
        }
        if (segStart == path.length && cur == null) {
            // Empty last segment (trailing '.').
            throw EditError.invalidPath(path.length);
        }
        segments.add(cur != null ? cur.toString()
                : Bytes.text(path, segStart, path.length));
        return segments;
    }

    private static void pushSegment(List<String> segments, StringBuilder cur,
            byte[] path, int from, int to) {
        segments.add(cur != null ? cur.toString() : Bytes.text(path, from, to));
    }

    /** UTF-8 sequence length by lead byte (1 for ASCII / stray continuation). */
    private static int utf8Len(byte b) {
        int c = b & 0xFF;
        if (c < 0x80) {
            return 1;
        }
        if (c >= 0xC0 && c <= 0xDF) {
            return 2;
        }
        if (c >= 0xE0 && c <= 0xEF) {
            return 3;
        }
        if (c >= 0xF0 && c <= 0xF7) {
            return 4;
        }
        return 1;
    }
}
