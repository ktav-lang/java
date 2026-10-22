package lang.ktav.pure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lang.ktav.Value;

/**
 * Comment-preserving formatter — a port of the Rust core's
 * {@code fmt_parser} (trivia-aware parser) plus {@code render::formatted}
 * (writer), backing {@code format(src)}.
 *
 * <p><b>Trivia model.</b> Spec § 3.4 makes every comment own a whole
 * physical line, so trivia is modeled as a run of {@link TriviaLine}s —
 * a blank line or a whole-line comment — attached to whichever construct
 * the NEXT content line produces (a pair, an array item, or, when the
 * content line is a closing {@code} / {@code]}, the compound being
 * closed as its <em>trailing</em> trivia). Runs before the very first
 * content line become the document's leading trivia; trivia after the
 * last content line becomes the root compound's trailing trivia.
 * Inline compounds and dotted-key intermediates never carry trivia of
 * their own (both are confined to one source line); a dotted-key line's
 * leading trivia attaches to the leaf slot only — the same documented
 * simplification the Rust formatter makes.
 *
 * <p><b>Preservation policy</b> (matched to the Rust
 * {@code PositionedParser}):
 * <ul>
 *   <li>Comments are preserved verbatim (the trimmed source line,
 *       starting at its {@code ##}), never collapsed; they are
 *       re-indented to their attachment point's depth on output.</li>
 *   <li>Blank lines survive as grouping hints, but a run of 2+ collapses
 *       to exactly one, and a blank is dropped entirely when it would
 *       pad the very start of the document or of a compound (the
 *       Rust {@code push_blank_trivia} "at start" rule).</li>
 *   <li>Blank padding at the END of a compound (before its closer) and
 *       at EOF is dropped (trailing blanks are trimmed whenever a trivia
 *       run becomes a trailing bucket). Comments are never dropped.</li>
 *   <li>Hence the fixed point: {@code format(format(x)) == format(x)}.</li>
 *   <li>Key order is never changed.</li>
 * </ul>
 *
 * <p><b>Line endings.</b> Input lines are split on LF, CR, and CRLF
 * (matching the Rust formatter's CR-aware fallback); the OUTPUT is
 * always LF-only — the Rust emitter pushes {@code '\n'} after every
 * line and never echoes the input's terminator style. The formatter
 * therefore normalizes CRLF/CR documents to LF.
 *
 * <p><b>Structure.</b> Emission reuses the canonical writer's rules
 * unchanged (indent, key quoting, § 5.9.8 floats, § 5.9.7 string forms)
 * — for documents with no comments and no blank lines the output equals
 * {@code emitCanonical(loads(src))}. Grammar errors are the parser's
 * (same categories, spans, and precedence as {@code loads}); § 5.9.0
 * writer rejections are {@code UnrepresentableAt} envelopes raised
 * during emission into a local buffer (never any partial output).
 */
final class PureFormatter {

    // ------------------------------------------------------------------
    // Trivia-carrying document model
    // ------------------------------------------------------------------

    /** One preserved line: a blank line or a whole-line comment. */
    static final class TriviaLine {
        final boolean blank;
        final String text; // comment text; trimmed source line starting with '#'

        TriviaLine(boolean blank, String text) {
            this.blank = blank;
            this.text = text;
        }
    }

    /** A pair's or item's trivia + value slot. */
    static final class Slot {
        final ArrayList<TriviaLine> trivia = new ArrayList<>();
        PNode value;
    }

    /** {@link Value} plus trivia only at compound boundaries. */
    static final class PNode {
        Value scalar; // Null / Bool / Int / Flt / Str
        PObj obj;
        PArr arr;

        static PNode of(Value v) {
            PNode n = new PNode();
            n.scalar = v;
            return n;
        }

        static PNode of(PObj o) {
            PNode n = new PNode();
            n.obj = o;
            return n;
        }

        static PNode of(PArr a) {
            PNode n = new PNode();
            n.arr = a;
            return n;
        }
    }

    static final class PObj {
        final LinkedHashMap<String, Slot> pairs = new LinkedHashMap<>();
        final ArrayList<TriviaLine> trailing = new ArrayList<>();
        boolean synthetic; // created by dotted-key expansion
        boolean atStart = true;
    }

    static final class PArr {
        final ArrayList<Slot> items = new ArrayList<>();
        final ArrayList<TriviaLine> trailing = new ArrayList<>();
        boolean atStart = true;
    }

    static final class FmtDoc {
        final ArrayList<TriviaLine> leading;
        final PNode root;

        FmtDoc(ArrayList<TriviaLine> leading, PNode root) {
            this.leading = leading;
            this.root = root;
        }
    }

    /** Lazy slot for a value whose multi-line body finishes later. */
    private static final class Holder {
        Slot slot; // pair slot or appended array slot to fill on closer

        void set(Value scalar) {
            slot.value = PNode.of(scalar);
        }
    }

    // ------------------------------------------------------------------
    // Parser state
    // ------------------------------------------------------------------

    private static final class Frame {
        static final int OBJ = 0;
        static final int ARR = 1;
        static final int ML = 2;

        final int kind;
        final boolean explicit;
        final PObj obj;
        final PArr arr;
        // ML frame state:
        final Holder holder;
        final boolean verbatim;
        final ArrayList<String> lines = new ArrayList<>();
        final int openerOffset;
        final int openerLine;

        Frame(int kind, boolean explicit, PObj obj, PArr arr,
                Holder holder, boolean verbatim, int openerOffset,
                int openerLine) {
            this.kind = kind;
            this.explicit = explicit;
            this.obj = obj;
            this.arr = arr;
            this.holder = holder;
            this.verbatim = verbatim;
            this.openerOffset = openerOffset;
            this.openerLine = openerLine;
        }

        ArrayList<TriviaLine> trailing() {
            return kind == OBJ ? obj.trailing : arr.trailing;
        }

        boolean isAtStart() {
            return kind == OBJ ? obj.atStart : arr.atStart;
        }

        void setStarted() {
            if (kind == OBJ) {
                obj.atStart = false;
            } else {
                arr.atStart = false;
            }
        }
    }

    private final Ctx ctx;
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private final ArrayList<TriviaLine> pending = new ArrayList<>();
    private ArrayList<TriviaLine> docLeading = new ArrayList<>();
    private PNode rootInline; // inline root (stamped), trivia-free until EOF
    private boolean rootSet;
    private boolean rootDone;
    private boolean rootExplicit;
    private PNode completedRoot; // explicit root already popped by closeFrame/mlLine

    private PureFormatter(byte[] b, int start) {
        this.ctx = new Ctx(b, start, false); // the formatter is never strict
    }

    /** Format a validated document from byte {@code start} (BOM skipped). */
    static String format(byte[] b, int start) {
        PureFormatter p = new PureFormatter(b, start);
        FmtDoc doc = p.run();
        return PureFormatterEmitter.emit(doc);
    }

    // ------------------------------------------------------------------
    // Line loop (splitting over LF / CR / CRLF, like PureParser)
    // ------------------------------------------------------------------

    private FmtDoc run() {
        byte[] b = ctx.b;
        int n = b.length;
        int i = ctx.start;
        ctx.line = 1;
        while (i < n) {
            int nl = i;
            while (nl < n && b[nl] != '\n' && b[nl] != '\r') {
                nl++;
            }
            int next = nl;
            if (nl < n) {
                next = (b[nl] == '\r' && nl + 1 < n && b[nl + 1] == '\n')
                        ? nl + 2 : nl + 1;
            }
            ctx.lineText = Bytes.text(b, i, nl);
            int[] t = Bytes.trim(b, i, nl);
            ctx.spanStart = t[0];
            ctx.spanEnd = t[1];
            handleLine(i, nl, t[0], t[1]);
            ctx.line++;
            i = next;
        }
        return finish();
    }

    private void handleLine(int rawStart, int rawEnd, int ts, int te) {
        byte[] b = ctx.b;
        // Inside an open multi-line string: trivia is impossible (§ 3.4).
        if (!stack.isEmpty() && stack.peek().kind == Frame.ML) {
            mlLine(rawStart, rawEnd, ts, te);
            return;
        }
        if (ts == te) {
            pushBlankTrivia();
            return;
        }
        if (te - ts >= 2 && b[ts] == '#' && b[ts + 1] == '#') {
            pushCommentTrivia(Bytes.text(b, ts, te));
            return;
        }
        if (rootDone) {
            throw orphan();
        }
        if (!rootSet) {
            detectRoot(ts, te); // consumes the line exactly once
            return;
        }
        if (te - ts == 1 && b[ts] == '}') {
            closeFrame(Frame.OBJ, '}');
            return;
        }
        if (te - ts == 1 && b[ts] == ']') {
            closeFrame(Frame.ARR, ']');
            return;
        }
        Frame top = stack.peek();
        top.setStarted();
        ArrayList<TriviaLine> trivia = takePending();
        if (top.kind == Frame.ARR) {
            arrayItem(ts, te, trivia);
        } else {
            pairLine(ts, te, trivia);
        }
    }

    // ------------------------------------------------------------------
    // Trivia accumulation (the Rust push_blank_trivia / push_comment_trivia)
    // ------------------------------------------------------------------

    private boolean currentAtStart() {
        if (stack.isEmpty()) {
            return !rootSet;
        }
        return stack.peek().isAtStart();
    }

    private void pushBlankTrivia() {
        if (pending.isEmpty() && currentAtStart()) {
            return; // blank padding at the very start: dropped
        }
        if (!pending.isEmpty() && pending.get(pending.size() - 1).blank) {
            return; // 2+ blanks collapse to one
        }
        pending.add(new TriviaLine(true, null));
    }

    private void pushCommentTrivia(String trimmed) {
        pending.add(new TriviaLine(false, trimmed));
    }

    private ArrayList<TriviaLine> takePending() {
        // Copy: docLeading and pending must not alias (pending keeps
        // accumulating for the rest of the document).
        ArrayList<TriviaLine> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }

    /** Pending trivia as a trailing bucket: trailing blanks dropped. */
    private static ArrayList<TriviaLine> trailingBucket(
            ArrayList<TriviaLine> trivia) {
        while (!trivia.isEmpty()
                && trivia.get(trivia.size() - 1).blank) {
            trivia.remove(trivia.size() - 1);
        }
        return trivia;
    }

    // ------------------------------------------------------------------
    // § 5.0.1 root detection (first content line, consumed once)
    // ------------------------------------------------------------------

    private void detectRoot(int ts, int te) {
        byte[] b = ctx.b;
        byte c = b[ts];
        if (te - ts == 1 && c == '{') {
            PObj root = new PObj();
            rootSet = true;
            rootExplicit = true;
            docLeading = takePending();
            stack.push(new Frame(Frame.OBJ, true, root, null, null,
                    false, ts, ctx.line));
            return;
        }
        if (te - ts == 1 && c == '[') {
            PArr root = new PArr();
            rootSet = true;
            rootExplicit = true;
            docLeading = takePending();
            stack.push(new Frame(Frame.ARR, true, null, root, null,
                    false, ts, ctx.line));
            return;
        }
        if (c == '{' || c == '[') {
            // § 5.0.1 rules 2–3 (closed inline root), same precedence as
            // PureParser: never a pair candidate, malformed compounds
            // diagnosed here.
            Inline.Parsed p = (c == '{')
                    ? Inline.parseObject(ctx, ts, te, 1)
                    : Inline.parseArray(ctx, ts, te, 1);
            if (p.next != te) {
                throw ctx.malformedInline(
                        "content after the compound's closer");
            }
            rootInline = stamp(Builders.toValue(p.value, 0));
            rootSet = true;
            rootDone = true;
            docLeading = takePending();
            return;
        }
        if (te - ts == 1 && (c == '}' || c == ']')) {
            throw unbalanced((char) c);
        }
        // § 5.0.1 rule 6: pair candidate (shape-only phase-1 test).
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
                PObj root = new PObj();
                rootSet = true;
                stack.push(new Frame(Frame.OBJ, false, root, null, null,
                        false, ts, ctx.line));
                pairLine(ts, te, takePending());
                return;
            }
        }
        // § 5.0.1 rule 7: array-item line.
        PArr root = new PArr();
        rootSet = true;
        stack.push(new Frame(Frame.ARR, false, null, root, null, false,
                ts, ctx.line));
        arrayItem(ts, te, takePending());
    }

    // ------------------------------------------------------------------
    // Compound close / orphan / errors
    // ------------------------------------------------------------------

    private void closeFrame(int wantKind, char closer) {
        Frame top = stack.peek();
        boolean closable = top.kind == wantKind
                && (stack.size() > 1 || top.explicit);
        if (!closable) {
            throw unbalanced(closer);
        }
        top.trailing().addAll(trailingBucket(takePending()));
        stack.pop();
        if (stack.isEmpty()) {
            rootDone = true;
            // Record the completed root explicitly: finish() must NEVER
            // pop an empty stack (P1 fix — NoSuchElementException on a
            // closed explicit root like "[\n1\n]").
            completedRoot = top.kind == Frame.OBJ
                    ? PNode.of(top.obj) : PNode.of(top.arr);
        }
    }

    private SourceError unbalanced(char found) {
        return ctx.syntax("UnbalancedBracket", "§6.1",
                "UnbalancedBracket at line " + ctx.line + ": unexpected '"
                        + found + "'");
    }

    private SourceError orphan() {
        return ctx.syntax("OrphanLineAfterTopLevelInline", "§6.14",
                "OrphanLineAfterTopLevelInline at line " + ctx.line
                        + ": content after the document root was completed");
    }

    // ------------------------------------------------------------------
    // § 5.3 pair lines
    // ------------------------------------------------------------------

    private void pairLine(int ts, int te, ArrayList<TriviaLine> trivia) {
        byte[] b = ctx.b;
        Keys.ColonResult cr = Keys.scanColon(b, ts, te);
        if (cr.kind == Keys.Colon.UNTERMINATED) {
            throw ctx.syntax("UnterminatedQuotedKey", "§6.16",
                    "UnterminatedQuotedKey at line " + ctx.line
                            + ": quoted key segment never closes");
        }
        if (cr.kind == Keys.Colon.ABSENT) {
            throw ctx.syntax("MissingSeparator", "§6.6",
                    "MissingSeparator at line " + ctx.line
                            + ": no ':' separator");
        }
        int ci = cr.index;
        int[] pre = Bytes.trim(b, ts, ci);
        if (pre[0] >= pre[1]) {
            throw ctx.syntax("EmptyKey", "§6.5",
                    "EmptyKey at line " + ctx.line + ": empty key");
        }
        Keys.RawPath key = Keys.splitKey(b, ts, ci, ctx.line, ctx.lineText,
                ctx.spanStart, ctx.spanEnd);
        if (ci + 1 < te && b[ci + 1] == ':') {
            // Raw marker (§ 5.3): sep-end required, escapes NOT processed.
            if (ci + 2 < te && Bytes.wsAt(b, ci + 2, te) == 0) {
                throw missingSepSpace();
            }
            int vs = Bytes.skipWs(b, ci + 2, te);
            insertLeaf(key, trivia, new Value.Str(Bytes.text(b, vs, te)));
            return;
        }
        if (ci + 1 < te && Bytes.wsAt(b, ci + 1, te) == 0) {
            throw missingSepSpace();
        }
        handleBody(key, trivia, Bytes.skipWs(b, ci + 1, te), te);
    }

    private SourceError missingSepSpace() {
        return ctx.syntax("MissingSeparatorSpace", "§6.10",
                "MissingSeparatorSpace at line " + ctx.line
                        + ": separator glued to its value");
    }

    /** § 5.2 dispatch for a multi-line pair body at {@code [vs, ve)}. */
    private void handleBody(Keys.RawPath key, ArrayList<TriviaLine> trivia,
            int vs, int ve) {
        byte[] b = ctx.b;
        if (vs >= ve) {
            insertLeaf(key, trivia, new Value.Str(""));
            return;
        }
        byte c = b[vs];
        if (c == '{' || c == '[') {
            if (ve - vs == 1) {
                openCompound(key, trivia,
                        c == '{' ? Scalars.Cls.OPEN_OBJECT
                                : Scalars.Cls.OPEN_ARRAY);
                return;
            }
            Inline.Parsed p = (c == '{')
                    ? Inline.parseObject(ctx, vs, ve, 1)
                    : Inline.parseArray(ctx, vs, ve, 1);
            if (p.next != ve) {
                throw ctx.malformedInline(
                        "content after the compound's closer");
            }
            insertLeaf(key, trivia, Builders.toValue(p.value, 0));
            return;
        }
        Scalars.Cls cl = Scalars.classify(Bytes.text(b, vs, ve), false,
                true, ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd,
                ctx.strict);
        dispatchScalarBody(key, trivia, cl, null);
    }

    /** Act on a classify() result in pair position ({@code slotKey} unused). */
    private void dispatchScalarBody(Keys.RawPath key,
            ArrayList<TriviaLine> trivia, Scalars.Cls cl, Slot arrSlot) {
        switch (cl.opener) {
            case Scalars.Cls.OPEN_OBJECT:
            case Scalars.Cls.OPEN_ARRAY:
                if (arrSlot != null) {
                    openCompoundItem(arrSlot, trivia, cl.opener);
                } else {
                    openCompound(key, trivia, cl.opener);
                }
                break;
            case Scalars.Cls.OPEN_ML_STRIPPED:
            case Scalars.Cls.OPEN_ML_VERBATIM:
                openMultiline(key, arrSlot, trivia,
                        cl.opener == Scalars.Cls.OPEN_ML_VERBATIM);
                break;
            default:
                if (arrSlot != null) {
                    arrSlot.value = PNode.of(cl.value);
                } else {
                    insertLeaf(key, trivia, cl.value);
                }
        }
    }

    // ------------------------------------------------------------------
    // Slot resolution (dotted keys) — Builders semantics on PObj
    // ------------------------------------------------------------------

    /** Resolve a leaf assignment; the slot's value is set immediately. */
    private void insertLeaf(Keys.RawPath segments,
            ArrayList<TriviaLine> trivia, Object value) {
        Slot slot = resolveLeaf(segments, trivia);
        slot.value = value instanceof PNode n ? n : PNode.of((Value) value);
    }

    /** Resolve (or create) the leaf slot for {@code segments}. */
    private Slot resolveLeaf(Keys.RawPath segments,
            ArrayList<TriviaLine> trivia) {
        List<String> decoded = new ArrayList<>(segments.size());
        PObj parent = walk(segments, decoded);
        String last = decoded.get(decoded.size() - 1);
        Slot existing = parent.pairs.get(last);
        if (existing != null) {
            throw occupied(parent, decoded, existing);
        }
        Slot slot = new Slot();
        slot.trivia.addAll(trivia);
        parent.pairs.put(last, slot);
        return slot;
    }

    /**
     * Resolve a compound opener's slot; the child PObj/PArr is created
     * and connected immediately so the closer only appends trailing
     * trivia. Mirrors Builders.compoundSlot's conflict table.
     */
    private Slot resolveCompound(Keys.RawPath segments,
            ArrayList<TriviaLine> trivia) {
        List<String> decoded = new ArrayList<>(segments.size());
        PObj parent = walk(segments, decoded);
        String last = decoded.get(decoded.size() - 1);
        Slot existing = parent.pairs.get(last);
        if (existing != null) {
            throw occupied(parent, decoded, existing);
        }
        Slot slot = new Slot();
        slot.trivia.addAll(trivia);
        parent.pairs.put(last, slot);
        return slot;
    }

    /**
     * Walk/create every intermediate object along {@code segments} minus
     * the last (reopen-merge of synthetic and explicit objects,
     * KeyPathConflict on a non-object intermediate), appending each
     * VALIDATED, DECODED segment to {@code decoded} as it is traversed
     * (§ 5.3.2 / Rust {@code build/insert.rs insert_dotted}): a blocked
     * prefix reports over the decoded prefix BEFORE the next segment's
     * escape defect can surface.
     */
    private PObj walk(Keys.RawPath segments, List<String> decoded) {
        if (segments.size() > PureParser.MAX_DEPTH) {
            throw new SourceError(
                    "dotted key path exceeds " + PureParser.MAX_DEPTH
                            + " segments",
                    "Other", null, null, null, null, null, null, null,
                    null, null);
        }
        Frame top = stack.peek();
        PObj cur = top.obj;
        for (int i = 0; i < segments.size() - 1; i++) {
            decoded.add(segments.decode(i));
            String seg = decoded.get(i);
            Slot existing = cur.pairs.get(seg);
            if (existing == null) {
                PObj created = new PObj();
                created.synthetic = true;
                Slot slot = new Slot();
                slot.value = PNode.of(created);
                cur.pairs.put(seg, slot);
                cur = created;
            } else if (existing.value != null && existing.value.obj != null) {
                cur = existing.value.obj;
            } else {
                // Envelope path = FULL raw-split with decode/raw
                // fallback (error/envelope.rs kind_path); the walk
                // decision above already used the decoded prefix only.
                throw SourceError.withPath("KeyPathConflict", "§6.3",
                        ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd,
                        segments.diagnosticPath(),
                        "KeyPathConflict at line " + ctx.line + ": '"
                                + String.join(".", segments.diagnosticPath())
                                + "'");
            }
        }
        decoded.add(segments.decode(segments.size() - 1));
        return cur;
    }

    /**
     * DuplicateKey / reverse KeyPathConflict at an occupied leaf,
     * mirroring Builders: a plain key naming a synthetic object is the
     * reverse conflict; anything else is a duplicate.
     */
    private SourceError occupied(PObj parent, List<String> segments,
            Slot existing) {
        boolean reverse = existing.value != null && existing.value.obj != null
                && existing.value.obj.synthetic;
        if (reverse) {
            return ctx.syntax("KeyPathConflict", "§6.3",
                    "KeyPathConflict at line " + ctx.line + ": '"
                            + String.join(".", segments) + "'");
        }
        return ctx.syntax("DuplicateKey", "§6.2",
                "DuplicateKey at line " + ctx.line + ": '"
                        + segments.get(segments.size() - 1) + "'");
    }

    // ------------------------------------------------------------------
    // Compound / multi-line openers
    // ------------------------------------------------------------------

    private void openCompound(Keys.RawPath key, ArrayList<TriviaLine> trivia,
            int opener) {
        Slot slot = resolveCompound(key, trivia);
        pushDepth();
        if (opener == Scalars.Cls.OPEN_OBJECT) {
            PObj child = new PObj();
            slot.value = PNode.of(child);
            stack.push(new Frame(Frame.OBJ, false, child, null, null,
                    false, ctx.spanStart, ctx.line));
        } else {
            PArr child = new PArr();
            slot.value = PNode.of(child);
            stack.push(new Frame(Frame.ARR, false, null, child, null,
                    false, ctx.spanStart, ctx.line));
        }
    }

    private void openCompoundItem(Slot arrSlot,
            ArrayList<TriviaLine> trivia, int opener) {
        pushDepth();
        arrSlot.trivia.addAll(trivia);
        if (opener == Scalars.Cls.OPEN_OBJECT) {
            PObj child = new PObj();
            arrSlot.value = PNode.of(child);
            stack.push(new Frame(Frame.OBJ, false, child, null, null,
                    false, ctx.spanStart, ctx.line));
        } else {
            PArr child = new PArr();
            arrSlot.value = PNode.of(child);
            stack.push(new Frame(Frame.ARR, false, null, child, null,
                    false, ctx.spanStart, ctx.line));
        }
    }

    /**
     * Open a multi-line string under a pair key or as an array item;
     * exactly one of {@code key} / {@code arrSlot} is used.
     */
    private void openMultiline(Keys.RawPath key, Slot arrSlot,
            ArrayList<TriviaLine> trivia, boolean verbatim) {
        Holder holder = new Holder();
        if (arrSlot != null) {
            arrSlot.trivia.addAll(trivia);
            holder.slot = arrSlot;
        } else {
            holder.slot = resolveLeaf(key, trivia);
        }
        pushDepth();
        stack.push(new Frame(Frame.ML, false, null, null, holder,
                verbatim, ctx.spanStart, ctx.line));
    }

    private void pushDepth() {
        if (stack.size() + 1 > PureParser.MAX_DEPTH) {
            throw ctx.depthExceeded();
        }
    }

    // ------------------------------------------------------------------
    // § 5.4 array-item lines
    // ------------------------------------------------------------------

    private void arrayItem(int ts, int te, ArrayList<TriviaLine> trivia) {
        byte[] b = ctx.b;
        Slot slot = new Slot();
        slot.trivia.addAll(trivia);
        stack.peek().arr.items.add(slot);
        if (te - ts >= 2 && b[ts] == ':' && b[ts + 1] == ':') {
            // Raw-marker item (§ 5.4 rule 1): sep-end required.
            if (te > ts + 2 && Bytes.wsAt(b, ts + 2, te) == 0) {
                throw missingSepSpace();
            }
            int vs = Bytes.skipWs(b, ts + 2, te);
            slot.value = PNode.of(new Value.Str(Bytes.text(b, vs, te)));
            return;
        }
        if (te - ts >= 1 && (b[ts] == '{' || b[ts] == '[')) {
            byte c = b[ts];
            if (te - ts == 1) {
                openCompoundItem(slot, trivia0(),
                        c == '{' ? Scalars.Cls.OPEN_OBJECT
                                : Scalars.Cls.OPEN_ARRAY);
                return;
            }
            Inline.Parsed p = (c == '{')
                    ? Inline.parseObject(ctx, ts, te, 1)
                    : Inline.parseArray(ctx, ts, te, 1);
            if (p.next != te) {
                throw ctx.malformedInline(
                        "content after the compound's closer");
            }
            slot.value = stamp(Builders.toValue(p.value, 0));
            return;
        }
        Scalars.Cls cl = Scalars.classify(Bytes.text(b, ts, te), false,
                true, ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd,
                ctx.strict);
        dispatchScalarBody(null, null, cl, slot);
    }

    /** Empty trivia list (the slot already owns its trivia). */
    private static ArrayList<TriviaLine> trivia0() {
        return new ArrayList<>(0);
    }

    // ------------------------------------------------------------------
    // § 5.6 multi-line strings
    // ------------------------------------------------------------------

    private void mlLine(int rawStart, int rawEnd, int ts, int te) {
        byte[] b = ctx.b;
        Frame ml = stack.peek();
        boolean isCloser = ml.verbatim
                ? (te - ts == 2 && b[ts] == ')' && b[ts + 1] == ')')
                : (te - ts == 1 && b[ts] == ')');
        if (isCloser) {
            stack.pop();
            ml.holder.set(new Value.Str(buildMultiline(ml)));
            if (stack.isEmpty()) {
                rootDone = true;
                completedRoot = ml.holder.slot.value; // defensive: an ML string never opens as the root
            }
            return;
        }
        // The RAW line, terminators stripped — trailing whitespace is
        // preserved (the Rust formatter pushes the raw line too).
        ml.lines.add(Bytes.text(b, rawStart, rawEnd));
    }

    /** § 5.6 stripped/verbatim body assembly (mirrors PureParser). */
    private static String buildMultiline(Frame ml) {
        if (ml.verbatim) {
            return String.join("\n", ml.lines);
        }
        List<int[]> prefixes = new ArrayList<>();
        int common = Integer.MAX_VALUE;
        for (String line : ml.lines) {
            int[] ws = leadingWsCodePoints(line);
            if (ws == null) {
                continue;
            }
            prefixes.add(ws);
        }
        for (int[] ws : prefixes) {
            common = Math.min(common, ws.length);
        }
        if (common == Integer.MAX_VALUE) {
            common = 0;
        }
        outer:
        for (int k = 0; k < common; k++) {
            int cp = prefixes.get(0)[k];
            for (int[] ws : prefixes) {
                if (ws[k] != cp) {
                    common = k;
                    break outer;
                }
            }
        }
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (String line : ml.lines) {
            int[] ws = leadingWsCodePoints(line);
            if (!first) {
                out.append('\n');
            }
            first = false;
            if (ws == null) {
                continue;
            }
            int idx = 0;
            for (int k = 0; k < common; k++) {
                idx += Character.charCount(ws[k]);
            }
            int end = trimEndString(line, idx);
            out.append(line, idx, end);
        }
        return out.toString();
    }

    private static int[] leadingWsCodePoints(String s) {
        int[] tmp = new int[s.codePointCount(0, s.length())];
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (!isWsCodePoint(cp)) {
                break;
            }
            tmp[n++] = cp;
            i += Character.charCount(cp);
        }
        if (i >= s.length()) {
            return null;
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    private static boolean isWsCodePoint(int cp) {
        switch (cp) {
            case 0x09: case 0x0B: case 0x0C: case 0x20: case 0x85:
            case 0xA0: case 0x1680: case 0x2028: case 0x2029:
            case 0x202F: case 0x205F: case 0x3000:
                return true;
            default:
                return cp >= 0x2000 && cp <= 0x200A;
        }
    }

    private static int trimEndString(String s, int from) {
        int end = s.length();
        while (end > from) {
            int cp = s.codePointBefore(end);
            int len = Character.charCount(cp);
            if (isWsCodePoint(cp)) {
                end -= len;
            } else {
                break;
            }
        }
        return end;
    }

    // ------------------------------------------------------------------
    // EOF
    // ------------------------------------------------------------------

    /**
     * EOF. Explicit enumeration of the terminal states — in priority
     * order:
     * <ol>
     * <li>any remaining ML or explicit frame → UnclosedCompound (open
     *     multiline string / unfinished explicit root);</li>
     * <li>{@code rootInline != null} → the inline root;</li>
     * <li>{@code completedRoot != null} → an explicit root that
     *     closeFrame (or, defensively, mlLine) already popped — the
     *     stack is empty here, so popping would crash;</li>
     * <li>stack non-empty (implicit frames only survive the loop) →
     *     pop the single implicit root frame, as before;</li>
     * <li>{@code !rootSet} → empty / comments-only document: empty
     *     Object root, all trivia as leading.</li>
     * </ol>
     * The empty-document substitution is reached only when nothing was
     * ever parsed (no rootInline, no completedRoot, no frames).
     */
    private FmtDoc finish() {
        for (Frame f : stack) {
            if (f.kind == Frame.ML || f.explicit) {
                throw SourceError.unpositioned("UnclosedCompound", "§6.1",
                        f.openerOffset, ctx.b.length,
                        "UnclosedCompound: compound opened at line "
                                + f.openerLine + " is never closed");
            }
        }
        ArrayList<TriviaLine> eofTrivia = trailingBucket(takePending());
        if (rootInline != null) {
            attachTrailing(rootInline, eofTrivia);
            return new FmtDoc(docLeading, rootInline);
        }
        if (completedRoot != null) {
            attachTrailing(completedRoot, eofTrivia);
            return new FmtDoc(docLeading, completedRoot);
        }
        if (!stack.isEmpty()) {
            Frame rootFrame = stack.pop();
            PNode root = rootFrame.kind == Frame.OBJ
                    ? PNode.of(rootFrame.obj) : PNode.of(rootFrame.arr);
            attachTrailing(root, eofTrivia);
            return new FmtDoc(docLeading, root);
        }
        // Empty / comments-only document: empty Object root; all
        // trivia becomes the document's leading trivia.
        ArrayList<TriviaLine> leading = docLeading;
        leading.addAll(eofTrivia);
        return new FmtDoc(leading, PNode.of(new PObj()));
    }

    private static void attachTrailing(PNode root,
            ArrayList<TriviaLine> trivia) {
        if (root.obj != null) {
            root.obj.trailing.addAll(trivia);
        } else {
            root.arr.trailing.addAll(trivia);
        }
    }

    // ------------------------------------------------------------------
    // stamp(Value) — a parsed Value with empty trivia throughout
    // ------------------------------------------------------------------

    private static PNode stamp(Value v) {
        if (v instanceof Value.Obj o) {
            PObj obj = new PObj();
            for (Map.Entry<String, Value> e : o.entries().entrySet()) {
                Slot slot = new Slot();
                slot.value = stamp(e.getValue());
                obj.pairs.put(e.getKey(), slot);
            }
            return PNode.of(obj);
        }
        if (v instanceof Value.Arr a) {
            PArr arr = new PArr();
            for (Value item : a.items()) {
                Slot slot = new Slot();
                slot.value = stamp(item);
                arr.items.add(slot);
            }
            return PNode.of(arr);
        }
        return PNode.of(v);
    }

}
