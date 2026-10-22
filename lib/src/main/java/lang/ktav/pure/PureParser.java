package lang.ktav.pure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import lang.ktav.Value;

/**
 * The single-pass, line-oriented Ktav document parser (spec § 5). Works
 * on validated UTF-8 bytes; spans are ORIGINAL byte offsets into the
 * input, including any skipped BOM (§ 3.1 / 0.8.1 errata). See
 * {@code PureKtav} for the public entry points.
 *
 * <p>Nesting depth — multi-line compound frames, inline recursion, and
 * dotted-path length — is bounded by {@link #MAX_DEPTH} (256); ordinary
 * spec documents sit far below it. Exceeding the bound is an explicit
 * {@code Other} error, never a stack overflow.
 */
public final class PureParser {

    /** Declared nesting-depth bound (multi-line + inline + dotted paths). */
    public static final int MAX_DEPTH = 256;

    private PureParser() {
    }

    private static final class Mline {
        final boolean verbatim;
        final Builders.Slot slot;
        final ArrayList<String> lines = new ArrayList<>();

        Mline(boolean verbatim, Builders.Slot slot) {
            this.verbatim = verbatim;
            this.slot = slot;
        }
    }

    private static final class Frame {
        static final int OBJ = 0;
        static final int ARR = 1;
        static final int ML = 2;

        final int kind;
        final boolean explicit;
        final Builders.ObjBuilder obj;
        final Builders.ArrBuilder arr;
        final Mline ml;
        final int openerOffset;
        final int openerLine;

        Frame(int kind, boolean explicit, Builders.ObjBuilder obj,
                Builders.ArrBuilder arr, Mline ml, int openerOffset,
                int openerLine) {
            this.kind = kind;
            this.explicit = explicit;
            this.obj = obj;
            this.arr = arr;
            this.ml = ml;
            this.openerOffset = openerOffset;
            this.openerLine = openerLine;
        }
    }

    private final Ctx ctx;
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private Object rootValue; // ObjBuilder | ArrBuilder | Value
    private boolean rootSet;
    private boolean rootDone;

    private PureParser(byte[] b, int start, boolean strict) {
        this.ctx = new Ctx(b, start, strict);
    }

    /** Parse a validated document from byte {@code start} (BOM skipped). */
    static Value parse(byte[] b, int start, boolean strict) {
        PureParser p = new PureParser(b, start, strict);
        p.run();
        return Builders.toValue(p.rootValue, 0);
    }

    // ------------------------------------------------------------------
    // Line loop
    // ------------------------------------------------------------------

    private void run() {
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
            ctx.lineText = lineText(b, i, nl);
            int[] t = Bytes.trim(b, i, nl);
            ctx.spanStart = t[0];
            ctx.spanEnd = t[1];
            processLine(i, nl, t[0], t[1]);
            ctx.line++;
            i = next;
        }
        finish();
    }

    private static String lineText(byte[] b, int start, int end) {
        if (end > start && b[end - 1] == '\r') {
            end--;
        }
        return Bytes.text(b, start, end);
    }

    private void processLine(int rawStart, int rawEnd, int ts, int te) {
        byte[] b = ctx.b;
        // § 5.1 rule 3: inside an open multi-line string.
        if (!stack.isEmpty() && stack.peek().kind == Frame.ML) {
            // § 5.6: verbatim/stripped content keeps the RAW line
            // (only the terminator removed) — trailing whitespace of a
            // content line is significant for both forms.
            mlLine(rawStart, rawEnd);
            return;
        }
        if (ts == te) {
            return; // blank (§ 5.1 rule 1)
        }
        if (te - ts >= 2 && b[ts] == '#' && b[ts + 1] == '#') {
            return; // comment (§ 5.1 rule 2)
        }
        if (!rootSet) {
            detectRoot(ts, te); // § 5.0.1 — consumes the line exactly once
            return;
        }
        if (rootDone) {
            throw orphan();
        }
        if (te - ts == 1 && b[ts] == '}') {
            closeCompound(Frame.OBJ, '}');
            return;
        }
        if (te - ts == 1 && b[ts] == ']') {
            closeCompound(Frame.ARR, ']');
            return;
        }
        Frame top = stack.peek();
        if (top.kind == Frame.ARR) {
            arrayItem(ts, te);
        } else {
            pairLine(ts, te);
        }
    }

    // ------------------------------------------------------------------
    // § 5.0.1 root detection (first content line, consumed once)
    // ------------------------------------------------------------------

    private void detectRoot(int ts, int te) {
        byte[] b = ctx.b;
        byte c = b[ts];
        if (te - ts == 1 && c == '{') {
            Builders.ObjBuilder root = new Builders.ObjBuilder();
            rootValue = root;
            rootSet = true;
            stack.push(new Frame(Frame.OBJ, true, root, null, null, ts,
                    ctx.line));
            return;
        }
        if (te - ts == 1 && c == '[') {
            Builders.ArrBuilder root = new Builders.ArrBuilder();
            rootValue = root;
            rootSet = true;
            stack.push(new Frame(Frame.ARR, true, null, root, null, ts,
                    ctx.line));
            return;
        }
        if (c == '{' || c == '[') {
            // § 5.0.1 rules 2–3 (closed inline root) with the § 5.0.1
            // note's precedence over the pair-candidate test: the same
            // leading bracket is never a pair candidate, and an
            // unterminated/malformed compound is diagnosed here.
            Inline.Parsed p = (c == '{')
                    ? Inline.parseObject(ctx, ts, te, 1)
                    : Inline.parseArray(ctx, ts, te, 1);
            if (p.next != te) {
                throw ctx.malformedInline("content after the compound's closer");
            }
            rootValue = p.value;
            rootSet = true;
            rootDone = true;
            return;
        }
        if (te - ts == 1 && (c == '}' || c == ']')) {
            // § 5.0.1 rule 8 — bare close as the first content line.
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
                sepOk = true; // '::' is a pair separator even glued (§ 6.10)
            } else {
                sepOk = ci + 1 >= te || Bytes.wsAt(b, ci + 1, te) > 0;
            }
            if (nonEmptyPrefix && sepOk) {
                Builders.ObjBuilder root = new Builders.ObjBuilder();
                rootValue = root;
                rootSet = true;
                stack.push(new Frame(Frame.OBJ, false, root, null, null,
                        ts, ctx.line));
                pairLine(ts, te); // consumed once, as the first pair
                return;
            }
        }
        // § 5.0.1 rule 7: array-item line (covers no-separator lines,
        // unterminated quoted keys on the undecided first line, and
        // glued plain-':' lines).
        Builders.ArrBuilder root = new Builders.ArrBuilder();
        rootValue = root;
        rootSet = true;
        stack.push(new Frame(Frame.ARR, false, null, root, null, ts,
                ctx.line));
        arrayItem(ts, te);
    }

    // ------------------------------------------------------------------
    // Compound close / orphan / errors
    // ------------------------------------------------------------------

    private void closeCompound(int wantKind, char closer) {
        Frame top = stack.peek();
        boolean closable = top.kind == wantKind
                && (stack.size() > 1 || top.explicit);
        if (!closable) {
            throw unbalanced(closer);
        }
        stack.pop();
        if (stack.isEmpty()) {
            rootDone = true; // explicit root fully constructed (§ 6.14)
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

    private void pairLine(int ts, int te) {
        byte[] b = ctx.b;
        Keys.ColonResult cr = Keys.scanColon(b, ts, te);
        if (cr.kind == Keys.Colon.UNTERMINATED) {
            // § 6.16 — takes precedence over MissingSeparator.
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
        List<String> key = Keys.parseKey(b, ts, ci, ctx.line, ctx.lineText,
                ctx.spanStart, ctx.spanEnd);
        if (ci + 1 < te && b[ci + 1] == ':') {
            // Raw marker (§ 5.3): sep-end required, escapes NOT
            // processed (whole-line body), trailing ws already trimmed.
            if (ci + 2 < te && Bytes.wsAt(b, ci + 2, te) == 0) {
                throw missingSepSpace();
            }
            int vs = Bytes.skipWs(b, ci + 2, te);
            attachLeaf(key, new Value.Str(Bytes.text(b, vs, te)));
            return;
        }
        if (ci + 1 < te && Bytes.wsAt(b, ci + 1, te) == 0) {
            throw missingSepSpace();
        }
        handleBody(key, Bytes.skipWs(b, ci + 1, te), te);
    }

    private SourceError missingSepSpace() {
        return ctx.syntax("MissingSeparatorSpace", "§6.10",
                "MissingSeparatorSpace at line " + ctx.line
                        + ": separator glued to its value");
    }

    /** § 5.2 dispatch for a multi-line pair body at {@code [vs, ve)}. */
    private void handleBody(List<String> key, int vs, int ve) {
        byte[] b = ctx.b;
        if (vs >= ve) {
            // Empty value-part → empty String (§ 5.3).
            attachLeaf(key, new Value.Str(""));
            return;
        }
        byte c = b[vs];
        if (c == '{' || c == '[') {
            if (ve - vs == 1) {
                openCompound(key, c == '{'
                        ? Scalars.Cls.OPEN_OBJECT : Scalars.Cls.OPEN_ARRAY);
                return;
            }
            // § 5.2 rules 6–9: inline compound in value position.
            Inline.Parsed p = (c == '{')
                    ? Inline.parseObject(ctx, vs, ve, 1)
                    : Inline.parseArray(ctx, vs, ve, 1);
            if (p.next != ve) {
                throw ctx.malformedInline(
                        "content after the compound's closer");
            }
            attachLeaf(key, p.value);
            return;
        }
        Scalars.Cls cl = Scalars.classify(Bytes.text(b, vs, ve), false,
                true, ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd,
                ctx.strict);
        switch (cl.opener) {
            case Scalars.Cls.OPEN_OBJECT:
            case Scalars.Cls.OPEN_ARRAY:
                openCompound(key, cl.opener);
                break;
            case Scalars.Cls.OPEN_ML_STRIPPED:
            case Scalars.Cls.OPEN_ML_VERBATIM:
                openMultiline(key,
                        cl.opener == Scalars.Cls.OPEN_ML_VERBATIM);
                break;
            default:
                attachLeaf(key, cl.value);
        }
    }

    private void attachLeaf(List<String> key, Object value) {
        Builders.ObjBuilder top = stack.peek().obj;
        Builders.Slot slot = Builders.leafSlot(top, key, key.size() == 1,
                key, ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd);
        slot.set(value);
    }

    private void openCompound(List<String> key, int opener) {
        Builders.ObjBuilder top = stack.peek().obj;
        Builders.Slot slot = Builders.compoundSlot(top, key, key,
                ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd);
        if (opener == Scalars.Cls.OPEN_OBJECT) {
            Builders.ObjBuilder child = new Builders.ObjBuilder();
            slot.set(child);
            pushDepth();
            stack.push(new Frame(Frame.OBJ, false, child, null, null,
                    ctx.spanStart, ctx.line));
        } else {
            Builders.ArrBuilder child = new Builders.ArrBuilder();
            slot.set(child);
            pushDepth();
            stack.push(new Frame(Frame.ARR, false, null, child, null,
                    ctx.spanStart, ctx.line));
        }
    }

    private void openMultiline(List<String> key, boolean verbatim) {
        Builders.ObjBuilder top = stack.peek().obj;
        Builders.Slot slot = Builders.leafSlot(top, key, key.size() == 1,
                key, ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd);
        pushDepth();
        stack.push(new Frame(Frame.ML, false, null, null,
                new Mline(verbatim, slot), ctx.spanStart, ctx.line));
    }

    // ------------------------------------------------------------------
    // § 5.4 array-item lines
    // ------------------------------------------------------------------

    private void arrayItem(int ts, int te) {
        byte[] b = ctx.b;
        Builders.ArrBuilder top = stack.peek().arr;
        if (te - ts >= 2 && b[ts] == ':' && b[ts + 1] == ':') {
            // Raw-marker item (§ 5.4 rule 1): sep-end required.
            if (te > ts + 2 && Bytes.wsAt(b, ts + 2, te) == 0) {
                throw missingSepSpace();
            }
            int vs = Bytes.skipWs(b, ts + 2, te);
            top.items.add(new Value.Str(Bytes.text(b, vs, te)));
            return;
        }
        if (startsCompound(ts, te)) {
            byte c = b[ts];
            if (te - ts == 1) {
                pushDepth();
                if (c == '{') {
                    Builders.ObjBuilder child = new Builders.ObjBuilder();
                    top.items.add(child);
                    stack.push(new Frame(Frame.OBJ, false, child, null,
                            null, ts, ctx.line));
                } else {
                    Builders.ArrBuilder child = new Builders.ArrBuilder();
                    top.items.add(child);
                    stack.push(new Frame(Frame.ARR, false, null, child,
                            null, ts, ctx.line));
                }
                return;
            }
            Inline.Parsed p = (c == '{')
                    ? Inline.parseObject(ctx, ts, te, 1)
                    : Inline.parseArray(ctx, ts, te, 1);
            if (p.next != te) {
                throw ctx.malformedInline(
                        "content after the compound's closer");
            }
            top.items.add(p.value);
            return;
        }
        Scalars.Cls cl = Scalars.classify(Bytes.text(b, ts, te), false,
                true, ctx.line, ctx.lineText, ctx.spanStart, ctx.spanEnd,
                ctx.strict);
        switch (cl.opener) {
            case Scalars.Cls.OPEN_OBJECT: {
                pushDepth();
                Builders.ObjBuilder child = new Builders.ObjBuilder();
                top.items.add(child);
                stack.push(new Frame(Frame.OBJ, false, child, null, null,
                        ts, ctx.line));
                break;
            }
            case Scalars.Cls.OPEN_ARRAY: {
                pushDepth();
                Builders.ArrBuilder child = new Builders.ArrBuilder();
                top.items.add(child);
                stack.push(new Frame(Frame.ARR, false, null, child, null,
                        ts, ctx.line));
                break;
            }
            case Scalars.Cls.OPEN_ML_STRIPPED:
            case Scalars.Cls.OPEN_ML_VERBATIM: {
                pushDepth();
                Builders.Slot slot = Builders.appendSlot(top);
                stack.push(new Frame(Frame.ML, false, null, null,
                        new Mline(cl.opener == Scalars.Cls.OPEN_ML_VERBATIM,
                                slot),
                        ts, ctx.line));
                break;
            }
            default:
                top.items.add(cl.value);
        }
    }

    /** Body starts with '{' or '[' (candidate for openers / rules 6–9). */
    private boolean startsCompound(int ts, int te) {
        byte c = ctx.b[ts];
        return c == '{' || c == '[';
    }

    private void pushDepth() {
        if (stack.size() + 1 > MAX_DEPTH) {
            throw ctx.depthExceeded();
        }
    }

    // ------------------------------------------------------------------
    // § 5.6 multi-line strings
    // ------------------------------------------------------------------

    private void mlLine(int rawStart, int rawEnd) {
        byte[] b = ctx.b;
        Mline ml = stack.peek().ml;
        int[] t = Bytes.trim(b, rawStart, rawEnd);
        boolean isCloser = ml.verbatim
                ? (t[1] - t[0] == 2 && b[t[0]] == ')' && b[t[0] + 1] == ')')
                : (t[1] - t[0] == 1 && b[t[0]] == ')');
        if (isCloser) {
            stack.pop();
            ml.slot.set(buildMultiline(ml));
            if (stack.isEmpty()) {
                rootDone = true;
            }
            return;
        }
        ml.lines.add(Bytes.text(b, rawStart, rawEnd));
    }

    private String buildMultiline(Mline ml) {
        if (ml.verbatim) {
            return String.join("\n", ml.lines);
        }
        // Stripped form: common leading whitespace (in code points,
        // identical code-point-for-code-point) across non-blank lines,
        // then per-line trailing-whitespace strip, joined by LF.
        List<int[]> prefixes = new ArrayList<>();
        int common = Integer.MAX_VALUE;
        for (String line : ml.lines) {
            int[] ws = leadingWsCodePoints(line);
            if (ws == null) {
                continue; // blank line: no contribution
            }
            prefixes.add(ws);
        }
        for (int[] ws : prefixes) {
            common = Math.min(common, ws.length);
        }
        if (common == Integer.MAX_VALUE) {
            common = 0; // no non-blank lines
        }
        // The prefix must be identical code-point-for-code-point: a
        // tab-led line and a space-led line share nothing (§ 5.6).
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
        for (int i = 0; i < ml.lines.size(); i++) {
            String line = ml.lines.get(i);
            int[] ws = leadingWsCodePoints(line);
            if (!first) {
                out.append('\n');
            }
            first = false;
            if (ws == null) {
                continue; // blank → empty content line
            }
            // Skip the common prefix measured in code points.
            int idx = 0;
            for (int k = 0; k < common; k++) {
                idx += Character.charCount(ws[k]);
            }
            int end = trimEndString(line, idx);
            out.append(line, idx, end);
        }
        return out.toString();
    }

    /** Leading § 3.3 whitespace code points of {@code s}, or null if the line is blank. */
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
            return null; // blank line
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

    /** End index (exclusive) after trailing § 3.3 whitespace from {@code from}. */
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

    private void finish() {
        // Unclosed explicit roots, non-root compounds, and open
        // multi-line strings are UnclosedCompound (§ 6.1); an implicit
        // root needs no closer (0.8.1 errata).
        for (Frame f : stack) {
            if (f.kind == Frame.ML || f.explicit) {
                throw SourceError.unpositioned("UnclosedCompound", "§6.1",
                        f.openerOffset, ctx.b.length,
                        "UnclosedCompound: compound opened at line "
                                + f.openerLine + " is never closed");
            }
        }
        if (!rootSet) {
            rootValue = new Builders.ObjBuilder(); // empty Object root
        }
    }
}
