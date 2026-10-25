package lang.ktav.pure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Positional tree of the source: a line-by-line pass mirroring the
 * value parser's dispatch decisions, but recording BYTE RANGES of every
 * value instead of building trivia. Ported from the Rust core's
 * {@code document/parser/edit/tree.rs}: same line splitting (LF / lone
 * CR / CRLF, one EOL each), same root-shape classification, same
 * dotted-key / reopen insertion, same inline-compound positioning.
 *
 * <p>All offsets are ORIGINAL byte offsets into the source (a leading
 * BOM is skipped for content but stays in the coordinates).
 */
final class EditTree {

    // ── Slots ───────────────────────────────────────────────────────────

    /** How a value lies in the source; determines the splice bounds. */
    static final class Slot {
        static final int PAIR = 0;
        static final int ITEM = 1;
        static final int BLOCK = 2;

        final int kind;
        /** PAIR: first ':' byte. ITEM: first content byte (incl. "::"). */
        final int anchor; // -1 when absent
        /** Exact body bytes (BLOCK: opener..end of closing line). */
        final int b0;
        final int b1;

        private Slot(int kind, int anchor, int b0, int b1) {
            this.kind = kind;
            this.anchor = anchor;
            this.b0 = b0;
            this.b1 = b1;
        }

        static Slot pair(int colon, int b0, int b1) {
            return new Slot(PAIR, colon, b0, b1);
        }

        static Slot item(int start, int b0, int b1) {
            return new Slot(ITEM, start, b0, b1);
        }

        static Slot block(int anchor, int start, int end) {
            return new Slot(BLOCK, anchor, start, end);
        }
    }

    // ── Nodes ───────────────────────────────────────────────────────────

    static final class Node {
        static final int SCALAR = 0;
        static final int OBJECT = 1;
        static final int ARRAY = 2;

        final int kind;
        /** Built from an inline compound ({@code { ... }} / {@code [ ... ]}). */
        boolean inline;
        /** null — synthetic (dotted intermediate, root, or unattached inline). */
        Slot slot;
        final Map<String, Node> entries; // OBJECT
        final List<Node> items;          // ARRAY

        Node(int kind, boolean inline, Slot slot) {
            this.kind = kind;
            this.inline = inline;
            this.slot = slot;
            this.entries = kind == OBJECT ? new LinkedHashMap<>() : null;
            this.items = kind == ARRAY ? new ArrayList<>() : null;
        }

        Node child(String key) {
            return kind == OBJECT ? entries.get(key) : null;
        }

        Node item(int i) {
            return kind == ARRAY ? items.get(i) : null;
        }
    }

    // ── Builder ─────────────────────────────────────────────────────────

    private static final int ML_STRIPPED = 0;
    private static final int ML_VERBATIM = 1;

    private static final class Frame {
        final boolean isObject;
        final Node node;
        String pendingKey;
        /** ':' of the pair that opened the current pending block. */
        int pendingAnchor = -1;
        /** Opener byte of the block this frame will close. */
        final int rangeStart;

        Frame(boolean isObject, Node node, int rangeStart) {
            this.isObject = isObject;
            this.node = node;
            this.rangeStart = rangeStart;
        }
    }

    private final byte[] b;
    private final List<Frame> stack = new ArrayList<>();
    private int collecting = -1;
    private int collectingOpener;
    private boolean rootInitialized;
    private boolean rootConsumed;
    private Node rootInline;
    private boolean rootIsExplicit;

    private EditTree(byte[] b) {
        this.b = b;
    }

    /** Build the positional tree; returns (root, rootIsExplicit). */
    static Result build(byte[] b) {
        EditTree t = new EditTree(b);
        int n = b.length;
        int lineStart = (n >= 3 && (b[0] & 0xFF) == 0xEF
                && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) ? 3 : 0;
        int lineNum = 0;
        while (lineStart < n || (lineStart == n && n == 0)) {
            int pos = lineStart;
            while (pos < n && b[pos] != '\n' && b[pos] != '\r') {
                pos++;
            }
            lineNum++;
            t.handleLine(lineStart, pos, lineNum);
            if (pos >= n) {
                break;
            }
            lineStart = (b[pos] == '\r' && pos + 1 < n && b[pos + 1] == '\n')
                    ? pos + 2 : pos + 1;
        }
        return new Result(t.finish(), t.rootIsExplicit);
    }

    static final class Result {
        final Node root;
        final boolean rootExplicit;

        Result(Node root, boolean rootExplicit) {
            this.root = root;
            this.rootExplicit = rootExplicit;
        }
    }

    // ── Line handling ───────────────────────────────────────────────────

    private void handleLine(int rawStart, int rawEnd, int lineNum) {
        if (collecting >= 0) {
            int[] t = Bytes.trim(b, rawStart, rawEnd);
            String trimmed = Bytes.text(b, t[0], t[1]);
            String terminator = collecting == ML_VERBATIM ? "))" : ")";
            if (trimmed.equals(terminator)) {
                int opener = collectingOpener;
                collecting = -1;
                int end = t[1];
                Node node = new Node(Node.SCALAR, false,
                        Slot.block(-1, opener, end));
                attachScalar(node, lineNum);
            }
            // Content lines of the block are data — no tree effect.
            return;
        }

        int[] t = Bytes.trim(b, rawStart, rawEnd);
        int ts = t[0];
        int te = t[1];
        if (ts == te || (te - ts >= 2 && b[ts] == '#' && b[ts + 1] == '#')) {
            return; // blank / comment
        }

        if (rootConsumed) {
            throw EditError.unsupported(
                    "internal: orphan line after the document root");
        }

        if (!rootInitialized) {
            rootInitialized = true;
            if (!(te - ts == 1 && (b[ts] == '}' || b[ts] == ']'))) {
                if (b[ts] == '{' && te - ts == 1) {
                    rootIsExplicit = true;
                    pushObject(ts);
                    return;
                }
                if (b[ts] == '[' && te - ts == 1) {
                    rootIsExplicit = true;
                    pushArray(ts);
                    return;
                }
                if (b[ts] == '{' || b[ts] == '[') {
                    rootConsumed = true;
                    rootInline = positionInlineTop(ts, te);
                    return;
                }
                Keys.ColonResult cr = Keys.scanColon(b, ts, te);
                if (cr.kind == Keys.Colon.FOUND) {
                    int ci = cr.index;
                    int[] pre = Bytes.trim(b, ts, ci);
                    boolean nonEmptyPrefix = pre[0] < pre[1];
                    boolean sepOk;
                    if (ci + 1 < te && b[ci + 1] == ':') {
                        sepOk = true;
                    } else {
                        sepOk = ci + 1 >= te || Bytes.wsAt(b, ci + 1, te) > 0;
                    }
                    if (nonEmptyPrefix && sepOk) {
                        // Implicit Object root; the line is its first pair.
                        pushObject(0);
                        handleObjectPair(ts, te, lineNum);
                        return;
                    }
                }
                pushArray(0);
                handleArrayItem(ts, te, lineNum);
                return;
            }
            // Bare '}' / ']' as first content line — the real parser
            // already rejected the document; unreachable after parse.
            throw EditError.unsupported("internal: unbalanced bracket");
        }

        if (te - ts == 1 && b[ts] == '}') {
            closeFrame(true, lineNum, ts, te);
            return;
        }
        if (te - ts == 1 && b[ts] == ']') {
            closeFrame(false, lineNum, ts, te);
            return;
        }

        if (!stack.isEmpty() && !top().isObject) {
            handleArrayItem(ts, te, lineNum);
        } else {
            handleObjectPair(ts, te, lineNum);
        }
    }

    private Frame top() {
        return stack.get(stack.size() - 1);
    }

    private void pushObject(int rangeStart) {
        stack.add(new Frame(true, new Node(Node.OBJECT, false, null),
                rangeStart));
    }

    private void pushArray(int rangeStart) {
        stack.add(new Frame(false, new Node(Node.ARRAY, false, null),
                rangeStart));
    }

    // ── Object pairs ────────────────────────────────────────────────────

    private void handleObjectPair(int ts, int te, int lineNum) {
        Keys.ColonResult cr = Keys.scanColon(b, ts, te);
        if (cr.kind != Keys.Colon.FOUND) {
            throw EditError.unsupported(
                    "internal: pair without separator after a successful parse");
        }
        int colon = cr.index;
        int[] pre = Bytes.trim(b, ts, colon);
        String key = Bytes.text(b, pre[0], pre[1]);
        int colonOff = colon;

        if (colonOff + 1 < te && b[colonOff + 1] == ':') {
            // Raw marker '::'.
            if (colonOff + 2 < te && Bytes.wsAt(b, colonOff + 2, te) == 0) {
                throw EditError.unsupported(
                        "internal: separator glued to body");
            }
            int[] body = Bytes.trim(b, colonOff + 2, te);
            insertPair(key, new Node(Node.SCALAR, false,
                    Slot.pair(colonOff, body[0], te)), lineNum);
            return;
        }
        if (colonOff + 1 < te && Bytes.wsAt(b, colonOff + 1, te) == 0) {
            throw EditError.unsupported("internal: separator glued to body");
        }
        int vs = Bytes.skipWs(b, colonOff + 1, te);

        // classify_value_shape over [vs, te).
        int shape = classifyValueShape(vs, te);
        switch (shape) {
            case SHAPE_SCALAR:
            case SHAPE_EMPTY_STRING:
                insertPair(key, new Node(Node.SCALAR, false,
                        Slot.pair(colonOff, vs, te)), lineNum);
                return;
            case SHAPE_EMPTY_OBJECT:
                insertPair(key, new Node(Node.OBJECT, false,
                        Slot.pair(colonOff, vs, te)), lineNum);
                return;
            case SHAPE_EMPTY_ARRAY:
                insertPair(key, new Node(Node.ARRAY, false,
                        Slot.pair(colonOff, vs, te)), lineNum);
                return;
            case SHAPE_OPEN_OBJECT:
            case SHAPE_OPEN_ARRAY: {
                setPending(key, colonOff);
                if (shape == SHAPE_OPEN_OBJECT) {
                    pushObject(vs);
                } else {
                    pushArray(vs);
                }
                return;
            }
            case SHAPE_OPEN_ML_STRIPPED:
            case SHAPE_OPEN_ML_VERBATIM: {
                setPending(key, colonOff);
                collecting = shape == SHAPE_OPEN_ML_VERBATIM
                        ? ML_VERBATIM : ML_STRIPPED;
                collectingOpener = vs;
                return;
            }
            default: { // SHAPE_INLINE_VALUE
                Node node = positionInlineTop(vs, te);
                node.inline = false;
                node.slot = Slot.pair(colonOff, vs, te);
                insertPair(key, node, lineNum);
            }
        }
    }

    private void setPending(String key, int anchor) {
        top().pendingKey = key;
        top().pendingAnchor = anchor;
    }

    private void insertPair(String key, Node value, int lineNum) {
        insertValue(top().node, key, value);
    }

    // ── Array items ─────────────────────────────────────────────────────

    private void handleArrayItem(int ts, int te, int lineNum) {
        Frame f = top();
        if (te - ts >= 2 && b[ts] == ':' && b[ts + 1] == ':') {
            if (te > ts + 2 && Bytes.wsAt(b, ts + 2, te) == 0) {
                throw EditError.unsupported("internal: separator glued to body");
            }
            int[] body = Bytes.trim(b, ts + 2, te);
            f.node.items.add(new Node(Node.SCALAR, false,
                    Slot.item(ts, body[0], te)));
            return;
        }
        int shape = classifyValueShape(ts, te);
        int bodyStart = Bytes.skipWs(b, ts, te);
        switch (shape) {
            case SHAPE_SCALAR:
            case SHAPE_EMPTY_STRING:
                f.node.items.add(new Node(Node.SCALAR, false,
                        Slot.item(ts, bodyStart, te)));
                return;
            case SHAPE_EMPTY_OBJECT:
                f.node.items.add(new Node(Node.OBJECT, false,
                        Slot.item(ts, bodyStart, te)));
                return;
            case SHAPE_EMPTY_ARRAY:
                f.node.items.add(new Node(Node.ARRAY, false,
                        Slot.item(ts, bodyStart, te)));
                return;
            case SHAPE_OPEN_OBJECT:
                pushObject(bodyStart);
                return;
            case SHAPE_OPEN_ARRAY:
                pushArray(bodyStart);
                return;
            case SHAPE_OPEN_ML_STRIPPED:
            case SHAPE_OPEN_ML_VERBATIM:
                collecting = shape == SHAPE_OPEN_ML_VERBATIM
                        ? ML_VERBATIM : ML_STRIPPED;
                collectingOpener = bodyStart;
                return;
            default: {
                Node node = positionInlineTop(bodyStart, te);
                node.inline = false;
                node.slot = Slot.item(ts, bodyStart, te);
                f.node.items.add(node);
            }
        }
    }

    private void attachScalar(Node node, int lineNum) {
        Frame f = top();
        if (f.isObject) {
            String key = f.pendingKey;
            if (key == null) {
                throw EditError.unsupported(
                        "internal: multiline closed without pending key");
            }
            f.pendingKey = null;
            int anchor = f.pendingAnchor;
            f.pendingAnchor = -1;
            if (node.slot != null && node.slot.kind == Slot.BLOCK) {
                node = new Node(Node.SCALAR, false,
                        Slot.block(anchor, node.slot.b0, node.slot.b1));
            }
            insertValue(f.node, key, node);
        } else {
            f.node.items.add(node);
        }
    }

    private void closeFrame(boolean object, int lineNum, int ts, int te) {
        if (stack.size() == 1 && rootIsExplicit) {
            rootConsumed = true;
            return;
        }
        if (stack.size() <= 1) {
            throw EditError.unsupported("internal: unbalanced bracket");
        }
        Frame frame = stack.remove(stack.size() - 1);
        if (frame.isObject != object) {
            throw EditError.unsupported("internal: mismatched closer");
        }
        int anchor = -1;
        if (!stack.isEmpty() && top().isObject) {
            anchor = top().pendingAnchor;
            top().pendingAnchor = -1;
        }
        Node node = new Node(frame.node.kind, false,
                Slot.block(anchor, frame.rangeStart, te));
        Frame parent = top();
        if (parent.isObject) {
            String key = parent.pendingKey;
            if (key == null) {
                throw EditError.unsupported(
                        "internal: closed compound without pending key");
            }
            parent.pendingKey = null;
            insertValue(parent.node, key, node);
        } else {
            parent.node.items.add(node);
        }
    }

    private Node finish() {
        if (collecting >= 0 || stack.size() > 1
                || (rootIsExplicit && !rootConsumed && !stack.isEmpty())) {
            throw EditError.unsupported(
                    "internal: unclosed compound after a successful parse");
        }
        if (rootInline != null) {
            return rootInline;
        }
        if (stack.isEmpty()) {
            return new Node(Node.OBJECT, false, null); // empty/comment-only
        }
        return stack.remove(stack.size() - 1).node;
    }

    // ── classify_value_shape over a byte range ──────────────────────────

    static final int SHAPE_SCALAR = 0;
    static final int SHAPE_EMPTY_STRING = 1;
    static final int SHAPE_OPEN_OBJECT = 2;
    static final int SHAPE_OPEN_ARRAY = 3;
    static final int SHAPE_EMPTY_OBJECT = 4;
    static final int SHAPE_EMPTY_ARRAY = 5;
    static final int SHAPE_OPEN_ML_STRIPPED = 6;
    static final int SHAPE_OPEN_ML_VERBATIM = 7;
    static final int SHAPE_INLINE_VALUE = 8;

    /** {@code from} is already the first non-whitespace byte. */
    private int classifyValueShape(int from, int end) {
        int len = end - from;
        if (len == 1) {
            byte c = b0(from);
            if (c == '{') {
                return SHAPE_OPEN_OBJECT;
            }
            if (c == '[') {
                return SHAPE_OPEN_ARRAY;
            }
            if (c == '(') {
                return SHAPE_OPEN_ML_STRIPPED;
            }
        }
        if (len == 2 && b0(from) == '(' && b0(from + 1) == '(') {
            return SHAPE_OPEN_ML_VERBATIM;
        }
        if (len == 2 && b0(from) == '(' && b0(from + 1) == ')') {
            return SHAPE_EMPTY_STRING;
        }
        if (len == 4 && b0(from) == '(' && b0(from + 1) == '('
                && b0(from + 2) == ')' && b0(from + 3) == ')') {
            return SHAPE_EMPTY_STRING;
        }
        byte c = b0(from);
        if (c == '{') {
            int[] inner = Bytes.trim(b, from + 1, end - 1);
            if (b0(end - 1) == '}' && inner[0] >= inner[1]) {
                return SHAPE_EMPTY_OBJECT;
            }
            return SHAPE_INLINE_VALUE;
        }
        if (c == '[') {
            int[] inner = Bytes.trim(b, from + 1, end - 1);
            if (b0(end - 1) == ']' && inner[0] >= inner[1]) {
                return SHAPE_EMPTY_ARRAY;
            }
            return SHAPE_INLINE_VALUE;
        }
        return SHAPE_SCALAR;
    }

    private byte b0(int i) {
        return b[i];
    }

    // ── Inline positioning ──────────────────────────────────────────────

    /**
     * Position an inline compound whose body occupies {@code [from, to)}
     * (starts with the opener, ends with the matching closer). Root of
     * the recursion: builds the segment list once, then recurses.
     */
    private Node positionInlineTop(int from, int to) {
        return positionInline(from, to, 0);
    }

    private Node positionInline(int from, int to, int depth) {
        boolean isObject = b[from] == '{';
        List<int[]> segments = new ArrayList<>();
        splitTopLevel(from + 1, to - 1, isObject, segments);
        if (isObject) {
            Node table = new Node(Node.OBJECT, true, null);
            for (int[] seg : segments) {
                int s = seg[0];
                int e = seg[1];
                if (s >= e) {
                    continue; // trailing comma
                }
                Keys.ColonResult cr = Keys.scanColon(b, s, e);
                if (cr.kind != Keys.Colon.FOUND) {
                    throw EditError.unsupported(
                            "internal: inline pair has no separator");
                }
                int colon = cr.index;
                int[] keyRange = Bytes.trim(b, s, colon);
                String key = Bytes.text(b, keyRange[0], keyRange[1]);
                int v;
                boolean raw = colon + 1 < e && b[colon + 1] == ':';
                if (raw) {
                    v = Bytes.skipWs(b, colon + 2, e);
                } else {
                    v = Bytes.skipWs(b, colon + 1, e);
                }
                Node child = inlineChild(v, e, depth);
                child.slot = Slot.pair(colon, v, e);
                insertValue(table, key, child);
            }
            return table;
        }
        Node arr = new Node(Node.ARRAY, true, null);
        for (int[] seg : segments) {
            int s = seg[0];
            int e = seg[1];
            if (s >= e) {
                continue;
            }
            int v = Bytes.skipWs(b, s, e);
            Node child = inlineChild(v, e, depth);
            child.slot = Slot.item(s, v, e);
            arr.items.add(child);
        }
        return arr;
    }

    private Node inlineChild(int v, int e, int depth) {
        if (v < e && (b[v] == '{' || b[v] == '[')) {
            return positionInline(v, e, depth + 1);
        }
        return new Node(Node.SCALAR, true, null);
    }

    /**
     * § 5.8.5-aware top-level comma split of an inline compound body
     * {@code [from, to)}: only a value-start {@code {}/[} nests; quoted
     * key segments (object mode, segment-start positions) are
     * comma-opaque; escapes are skipped; a trailing comma yields no
     * final segment. Input already parsed successfully — any surprise is
     * an internal error.
     */
    private void splitTopLevel(int from, int to, boolean isObject,
            List<int[]> out) {
        int i = from;
        int segAt = from;
        int depth = 0;
        int[] scopeObj = new int[64];
        int top = -1;
        boolean inKey = isObject;
        boolean valueStart = !isObject;
        while (i < to) {
            byte c = b[i];
            if (c == '\\') {
                i += 2; // escape lead consumes the next byte
                continue;
            }
            if (inKey && (c == '"' || c == '\'' || c == '`')) {
                int close = Keys.quotedSpanEnd(b, i, to);
                if (close < 0) {
                    throw EditError.unsupported(
                            "internal: unterminated quoted key segment");
                }
                i = close + 1;
                continue;
            }
            if (inKey) {
                if (c == ':') {
                    inKey = false;
                    valueStart = true;
                    i++;
                    continue;
                }
                if (c == '.') {
                    i = Bytes.skipWs(b, i + 1, to);
                    continue;
                }
                i++;
                continue;
            }
            if (valueStart && (c == '{' || c == '[')) {
                depth++;
                top++;
                scopeObj[top] = c == '{' ? 1 : 0;
                inKey = c == '{';
                valueStart = c != '{';
                i++;
                continue;
            }
            if (depth > 0 && (c == '}' || c == ']')) {
                depth--;
                top--;
                inKey = false; // the parent was in a value position
                valueStart = false;
                i++;
                continue;
            }
            if (c == ',' && depth == 0) {
                out.add(new int[] { segAt, i });
                i = Bytes.skipWs(b, i + 1, to);
                segAt = i;
                valueStart = true;
                inKey = isObject;
                continue;
            }
            valueStart = false;
            i++;
        }
        int[] tail = Bytes.trim(b, segAt, to);
        if (tail[0] >= tail[1] && segAt != from) {
            // Only whitespace after the last comma — trailing comma.
            return;
        }
        out.add(new int[] { segAt, to });
    }

    // ── Dotted-key insertion ────────────────────────────────────────────

    /**
     * Dotted-key expansion with reopen semantics: every intermediate
     * segment descends through (or creates) a synthetic object whose
     * slot is invalidated; the final segment inserts the value.
     * Occupied-by-non-object and duplicate collisions are unreachable
     * after a successful parse — reported as internal Unsupported.
     */
    private static void insertValue(Node table, String key, Node value) {
        List<int[]> ranges = splitKeyRanges(key);
        Node cur = table;
        for (int i = 0; i < ranges.size(); i++) {
            String seg = decodeSegment(key, ranges.get(i));
            boolean last = i == ranges.size() - 1;
            if (last) {
                if (cur.entries.containsKey(seg)) {
                    // Reopen: keep the existing entry, mark noncontiguous.
                    cur.entries.get(seg).slot = null;
                } else {
                    cur.entries.put(seg, value);
                }
            } else {
                Node next = cur.entries.get(seg);
                if (next == null) {
                    next = new Node(Node.OBJECT, false, null);
                    cur.entries.put(seg, next);
                } else if (next.kind != Node.OBJECT) {
                    throw EditError.unsupported(
                            "internal: dotted descent through a scalar");
                } else {
                    next.slot = null; // reopen / dotted intermediate
                }
                cur = next;
            }
        }
    }

    /** Raw dotted-path segment ranges of an already-decoded key. */
    private static List<int[]> splitKeyRanges(String key) {
        // The key here is the raw (post-trim) key spelling decoded from
        // source bytes; re-split on unescaped dots using the byte view.
        byte[] kb = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Keys.splitPath(kb, 0, kb.length);
    }

    private static String decodeSegment(String key, int[] range) {
        byte[] kb = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Keys.Segment s = Keys.checkAndDecode(kb, range[0], range[1],
                1, "", 0, 0);
        if (s.kind == Keys.Segment.OK) {
            return s.decoded;
        }
        throw EditError.unsupported("internal: invalid dotted key segment");
    }
}
