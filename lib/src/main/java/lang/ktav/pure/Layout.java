package lang.ktav.pure;

import java.util.ArrayList;
import java.util.List;

import lang.ktav.Value;

/**
 * Minimal inline-wrapper expansion when a splice would cost readability
 * — the port of the Rust core's {@code splice/layout.rs}: expand the
 * containing inline wrapper (and touched nested wrappers) only while a
 * criterion holds (multiline replacement, line growth past
 * max(100 Unicode scalar values, the old physical line length), or
 * string replacements needing ≥ 4 escapes with ≥ 25% overhead);untouched
 * nested branches stay inline. If a lossless standalone form does not
 * exist, the valid inline candidate is kept instead of failing.
 */
final class Layout {

    private Layout() {
    }

    static String adjust(byte[] source, String candidate, EditTree.Node root,
            List<String> path, Value replacement, int from, int to,
            int inserted) {
        long[] owner = inlineOwner(source, root, path, from);
        if (owner == null) {
            return candidate;
        }
        int start = (int) owner[0];
        int end = (int) owner[1];
        if (from < start) {
            return candidate;
        }
        int newEnd = end - (to - from) + inserted;
        if (newEnd < start) {
            throw EditError.unsupported("invalid layout range");
        }
        byte[] cb = candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (newEnd > cb.length || end > cb.length) {
            throw EditError.unsupported("invalid layout boundaries");
        }
        String body = Bytes.text(cb, start, newEnd);
        if (!isCompound(body)) {
            return candidate;
        }
        int oldWidth = codePoints(physicalLineText(cb, from));
        int limit = Math.max(oldWidth, 100);
        boolean force = requiresBlock(replacement, 0);
        if (!force && codePoints(physicalLineText(cb, from)) <= limit) {
            return candidate;
        }
        String indent = lineIndent(source, start);
        String eol = detectEol(source, end);
        int ch0 = from - start;
        int ch1 = from - start + inserted;
        String expanded = expand(cb, start, newEnd, indent, eol, limit,
                ch0, ch1, force, 0);
        if (expanded == null) {
            return candidate;
        }
        return Bytes.text(cb, 0, start) + expanded
                + Bytes.text(cb, newEnd, cb.length);
    }

    // ── Owner discovery ─────────────────────────────────────────────────

    private static long[] inlineOwner(byte[] source, EditTree.Node root,
            List<String> path, int pos) {
        EditTree.Node node = root;
        if (root.inline) {
            long[] lr = physicalLineRange(source, pos);
            int[] t = Bytes.trim(source, (int) lr[0], (int) lr[1]);
            int start = t[0];
            if (start + 2 < source.length
                    && (source[start] & 0xFF) == 0xEF
                    && (source[start + 1] & 0xFF) == 0xBB
                    && (source[start + 2] & 0xFF) == 0xBF) {
                start += 3;
            }
            return new long[] { start, t[1] };
        }
        for (String segment : path) {
            if (node.kind == EditTree.Node.OBJECT) {
                node = node.child(segment);
            } else if (node.kind == EditTree.Node.ARRAY) {
                node = node.item(EditSplice.parseIndex(segment));
            } else {
                node = null;
            }
            if (node == null) {
                throw EditError.notFound();
            }
            if (node.slot != null) {
                int start;
                int end;
                if (node.slot.kind == EditTree.Slot.BLOCK) {
                    start = node.slot.b0;
                    end = node.slot.b1;
                } else {
                    start = node.slot.b0;
                    end = node.slot.b1;
                }
                if ((node.kind == EditTree.Node.OBJECT
                        || node.kind == EditTree.Node.ARRAY)
                        && start >= 0 && end <= source.length
                        && isCompound(Bytes.text(source, start, end))
                        && !hasEol(source, start, end)) {
                    return new long[] { start, end };
                }
            }
        }
        if (node.inline) {
            long[] lr = physicalLineRange(source, pos);
            int[] t = Bytes.trim(source, (int) lr[0], (int) lr[1]);
            int lineStart = t[0];
            if (lineStart + 2 < source.length
                    && (source[lineStart] & 0xFF) == 0xEF
                    && (source[lineStart + 1] & 0xFF) == 0xBB
                    && (source[lineStart + 2] & 0xFF) == 0xBF) {
                lineStart += 3;
            }
            byte[] lb = Bytes.text(source, lineStart, t[1])
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String line = Bytes.text(source, lineStart, t[1]);
            byte[] bodyB;
            if (isCompound(line)) {
                bodyB = lb;
            } else {
                Keys.ColonResult cr = Keys.scanColon(lb, 0, lb.length);
                if (cr.kind != Keys.Colon.FOUND) {
                    return null;
                }
                int[] v = Bytes.trim(lb, cr.index + 1, lb.length);
                if (v[0] >= v[1]) {
                    return null;
                }
                byte[] sub = new byte[v[1] - v[0]];
                System.arraycopy(lb, v[0], sub, 0, sub.length);
                bodyB = sub;
            }
            String body = Bytes.text(bodyB, 0, bodyB.length);
            if (isCompound(body)) {
                int off = indexOfBytes(lb, bodyB);
                if (off < 0) {
                    return null;
                }
                return new long[] { lineStart + off, lineStart + off + bodyB.length };
            }
        }
        return null;
    }

    private static int indexOfBytes(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // ── Block criteria ──────────────────────────────────────────────────

    private static boolean requiresBlock(Value value, int depth) {
        if (depth > PureParser.MAX_DEPTH) {
            throw EditError.unsupported(
                    "replacement nesting exceeds inline depth limit ("
                            + PureParser.MAX_DEPTH + ")");
        }
        if (value instanceof Value.Str s) {
            String sv = s.value();
            if (sv.indexOf('\n') >= 0 || sv.indexOf('\r') >= 0) {
                return true;
            }
            String encoded = EditSplice.encodeInline(sv, false);
            int escapes = 0;
            for (int i = 0; i < encoded.length(); i++) {
                if (encoded.charAt(i) == '\\') {
                    escapes++;
                    i++;
                }
            }
            int plain = codePoints(sv);
            int overhead = codePoints(encoded) - plain;
            int quarter = plain / 4 + ((plain % 4 != 0) ? 1 : 0);
            return escapes >= 4 && overhead >= Math.max(quarter, 0);
        }
        if (value instanceof Value.Obj o) {
            for (Value child : o.entries().values()) {
                if (requiresBlock(child, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Value.Arr a) {
            for (Value child : a.items()) {
                if (requiresBlock(child, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    // ── Expansion ───────────────────────────────────────────────────────

    /**
     * Expand the inline compound occupying cb[bodyStart..bodyEnd).
     * {@code changed} is the splice range relative to the body start.
     * Returns null when no lossless standalone form exists.
     */
    private static String expand(byte[] cb, int bodyStart, int bodyEnd,
            String indent, String eol, int limit, int ch0, int ch1,
            boolean force, int depth) {
        if (depth > PureParser.MAX_DEPTH) {
            throw EditError.unsupported("layout nesting exceeds inline depth limit");
        }
        boolean object = cb[bodyStart] == '{';
        List<int[]> parts = new ArrayList<>();
        splitTopLevel(cb, bodyStart + 1, bodyEnd - 1, object, parts);
        String childIndent = indent + "    ";
        StringBuilder out = new StringBuilder(bodyEnd - bodyStart);
        out.append(object ? '{' : '[');
        for (int[] part : parts) {
            int ps = part[0];
            int pe = part[1];
            int[] t = Bytes.trim(cb, ps, pe);
            if (t[0] >= t[1]) {
                continue; // trailing comma
            }
            ps = t[0];
            pe = t[1];
            int offset = ps - bodyStart;
            int partLen = pe - ps;
            boolean touched = offset < ch1 && offset + partLen > ch0;
            Keys.ColonResult cr = null;
            boolean raw = false;
            if (object) {
                cr = Keys.scanColon(cb, ps, pe);
                if (cr.kind != Keys.Colon.FOUND) {
                    throw EditError.unsupported("inline pair has no separator");
                }
                raw = cr.index + 1 < pe && cb[cr.index + 1] == ':';
            }
            out.append(eol);
            out.append(childIndent);
            boolean partCompound = isCompound(Bytes.text(cb, ps, pe));
            if (!raw && partCompound && touched
                    && (force || codePoints(childIndent)
                            + codePoints(Bytes.text(cb, ps, pe)) > limit)) {
                int local0 = Math.max(ch0 - offset, 0);
                int local1 = Math.max(ch1 - offset, 0);
                String child = expand(cb, ps, pe, childIndent, eol, limit,
                        local0, local1, force, depth + 1);
                if (child == null) {
                    return null;
                }
                out.append(child);
            } else {
                String child = standalone(cb, ps, pe, raw, object,
                        childIndent, eol);
                if (child == null) {
                    return null;
                }
                out.append(child);
            }
        }
        out.append(eol);
        out.append(indent);
        out.append(object ? '}' : ']');
        return out.toString();
    }

    /**
     * Move one inline part to its own line: keep the spelling when the
     * standalone form parses identically; otherwise decode and re-plan.
     * Returns null when no lossless standalone form exists.
     */
    private static String standalone(byte[] cb, int ps, int pe,
            boolean raw, boolean object, String indent, String eol) {
        String part = Bytes.text(cb, ps, pe);
        String inline = object ? "{" + part + "}" : "[" + part + "]";
        // A probe failure is not fatal: fall through to decode+re-plan,
        // which re-emits the value canonically (the reference keeps the
        // part when the probe proves the spelling lossless).
        Value expected = tryParseInlineFragment(inline);
        // The standalone-form probe uses the REAL parser on the would-be
        // line (a pair line for object parts, a block array for array
        // parts) — escapes are NOT decoded there, exactly like the
        // reference's `parse(&line)`: if the standalone line re-parses
        // to the same value, the spelling moves up verbatim.
        Value parsedLine = expected == null
                ? null : tryParseLineFragment(part, object);
        if (parsedLine != null && parsedLine.equals(expected)) {
            return part;
        }
        int vs;
        int colon = -1;
        if (object) {
            Keys.ColonResult cr = Keys.scanColon(cb, ps, pe);
            if (cr.kind != Keys.Colon.FOUND) {
                throw EditError.unsupported("moved pair has no separator");
            }
            colon = cr.index;
            vs = raw ? Bytes.skipWs(cb, colon + 2, pe)
                    : Bytes.skipWs(cb, colon + 1, pe);
        } else {
            vs = Bytes.skipWs(cb, ps, pe);
        }
        Value value;
        if (raw) {
            String decoded = decodeInlineEscapes(Bytes.text(cb, vs, pe));
            if (decoded == null) {
                throw EditError.unsupported("invalid inline string");
            }
            value = new Value.Str(decoded);
        } else {
            Value parsed = parseInlineFragment("[" + part + "]", false,
                    "cannot decode moved inline value");
            if (!(parsed instanceof Value.Arr arr) || arr.items().size() != 1) {
                throw EditError.unsupported(
                        "moved inline value is not a single value");
            }
            value = arr.items().get(0);
        }
        if (value instanceof Value.Str s) {
            String sv = s.value();
            if (sv.indexOf('\r') >= 0) {
                return null;
            }
            for (String l : sv.split("\n", -1)) {
                if (trimSpacesTabs(l).equals("))")) {
                    return null;
                }
            }
        }
        EditSplicePlan p = new EditSplicePlan();
        p.slot = object ? EditTree.Slot.PAIR : EditTree.Slot.ITEM;
        p.existingRaw = raw;
        p.indent = indent;
        p.eol = eol;
        p.inlineCompound = true;
        Planned planned = planValuePublic(value, p);
        StringBuilder out = new StringBuilder();
        if (object) {
            out.append(Bytes.text(cb, ps, colon + 1));
            if (planned.desiredRaw) {
                out.append(':');
            }
            int wsFrom = raw ? colon + 2 : colon + 1;
            String ws = Bytes.text(cb, wsFrom, vs);
            out.append(ws.isEmpty() ? " " : ws);
        } else if (planned.desiredRaw) {
            out.append(":: ");
        }
        out.append(planned.body);
        return out.toString();
    }

    // ── Shared small helpers ────────────────────────────────────────────

    private static final class EditSplicePlan {
        int slot;
        boolean existingRaw;
        String indent;
        String eol;
        boolean inlineCompound;
    }

    private static Planned planValuePublic(Value v, EditSplicePlan p) {
        // Local re-export of planValue with the reduced context used by
        // standalone(): mirrors layout.rs's PlanCtx construction.
        if (v instanceof Value.Str s) {
            String sv = s.value();
            if (sv.indexOf('\r') >= 0) {
                throw EditError.unsupported(
                        "replacement string contains CR (U+000D)");
            }
            boolean edgeWs = (!sv.isEmpty() && isWsCp(sv.codePointAt(0)))
                    || (!sv.isEmpty()
                            && isWsCp(sv.codePointBefore(sv.length())));
            if (sv.indexOf('\n') >= 0 || edgeWs) {
                return new Planned(verbatimBlock(sv, p.indent, p.eol), false);
            }
            boolean desiredRaw;
            if (p.slot == EditTree.Slot.ITEM) {
                desiredRaw = EditSplice.itemNeedsRawMarker(sv) || sv.isEmpty();
            } else {
                desiredRaw = EditSplice.needsRawMarker(sv);
            }
            if (p.existingRaw) {
                desiredRaw = true;
            }
            return new Planned(sv, desiredRaw);
        }
        if (p.inlineCompound && (v instanceof Value.Obj || v instanceof Value.Arr)) {
            StringBuilder out = new StringBuilder();
            EditSplice.appendInlineValue(v, out);
            return new Planned(out.toString(), false);
        }
        throw EditError.unsupported("internal: unexpected layout plan value");
    }

    private static String verbatimBlock(String s, String indent, String eol) {
        for (String line : s.split("\n", -1)) {
            if (line.trim().equals("))")) {
                throw EditError.unsupported(
                        "multiline string contains a line equal to '))';"
                                + " no lossless verbatim form exists");
            }
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        out.append("((").append(eol);
        boolean first = true;
        for (String line : s.split("\n", -1)) {
            if (!first) {
                out.append(eol);
            }
            first = false;
            out.append(line);
        }
        out.append(eol).append(indent).append("))");
        return out.toString();
    }

    /** Top-level comma split over a byte range of the inline body. */
    private static void splitTopLevel(byte[] b, int from, int to,
            boolean isObject, List<int[]> out) {
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
                i += 2;
                continue;
            }
            if (inKey && (c == '"' || c == '\'' || c == '`')) {
                int close = Keys.quotedSpanEnd(b, i, to);
                if (close < 0) {
                    throw EditError.unsupported("cannot split inline layout");
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
                inKey = false;
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
            int w = Bytes.wsAt(b, i, to);
            if (w > 0) {
                i += w; // § 3.3 whitespace keeps the value-start armed
                continue;
            }
            valueStart = false;
            i++;
        }
        int[] tail = Bytes.trim(b, segAt, to);
        if (tail[0] >= tail[1] && segAt != from) {
            return; // trailing comma
        }
        out.add(new int[] { segAt, to });
    }

    /** Parse a synthetic inline fragment; throws Unsupported on failure. */
    private static Value parseInlineFragment(String text, boolean object,
            String why) {
        Value v = tryParseInlineFragment(text);
        if (v == null) {
            throw EditError.unsupported(why);
        }
        return v;
    }

    private static Value tryParseInlineFragment(String text) {
        try {
            byte[] fb = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Ctx ctx = new Ctx(fb, 0, false);
            ctx.line = 1;
            ctx.lineText = text;
            ctx.spanStart = 0;
            ctx.spanEnd = fb.length;
            Inline.Parsed p = fb[0] == '{'
                    ? Inline.parseObject(ctx, 0, fb.length, 1)
                    : Inline.parseArray(ctx, 0, fb.length, 1);
            if (p.next != fb.length) {
                return null;
            }
            return Builders.toValue(p.value, 0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Parse the would-be STANDALONE form with the real document parser:
     * an object part becomes a one-pair document, an array part becomes
     * a {@code [\n part \n]} block array. Escape sequences in plain
     * bodies are NOT decoded there — this is the reference's
     * {@code parse(&line)} probe.
     */
    private static Value tryParseLineFragment(String part, boolean object) {
        try {
            String doc = object ? part + "\n" : "[\n" + part + "\n]";
            byte[] fb = doc.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Value v = PureParser.parse(fb, 0, false);
            // Reject shape mismatches (e.g. a comment-only object part).
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Decode § 3.7 escapes of an inline string body; null when invalid. */
    private static String decodeInlineEscapes(String body) {
        try {
            byte[] bb = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder out = new StringBuilder(bb.length);
            Escapes.decode(bb, 0, bb.length, out, 1, body, 0, bb.length);
            return out.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Trim only ' ' and '\t' (the reference's `trim_matches(' '|'\t')`). */
    private static String trimSpacesTabs(String s) {
        int b = 0;
        int e = s.length();
        while (b < e && (s.charAt(b) == ' ' || s.charAt(b) == '\t')) {
            b++;
        }
        while (e > b && (s.charAt(e - 1) == ' ' || s.charAt(e - 1) == '\t')) {
            e--;
        }
        return s.substring(b, e);
    }

    private static boolean isCompound(String body) {
        return (body.startsWith("{") && body.endsWith("}"))
                || (body.startsWith("[") && body.endsWith("]"));
    }

    private static boolean hasEol(byte[] b, int from, int to) {
        for (int i = from; i < to && i < b.length; i++) {
            if (b[i] == '\n' || b[i] == '\r') {
                return true;
            }
        }
        return false;
    }

    private static long[] physicalLineRange(byte[] b, int pos) {
        int start = pos;
        while (start > 0 && b[start - 1] != '\n' && b[start - 1] != '\r') {
            start--;
        }
        int end = pos;
        while (end < b.length && b[end] != '\n' && b[end] != '\r') {
            end++;
        }
        return new long[] { start, end };
    }

    private static String physicalLineText(byte[] b, int pos) {
        long[] r = physicalLineRange(b, pos);
        return Bytes.text(b, (int) r[0], (int) r[1]);
    }

    private static int codePoints(String s) {
        return s.codePointCount(0, s.length());
    }

    private static String lineIndent(byte[] b, int pos) {
        int start = pos;
        while (start > 0 && b[start - 1] != '\n' && b[start - 1] != '\r') {
            start--;
        }
        int end = start;
        while (end < b.length && (b[end] == ' ' || b[end] == '\t')) {
            end++;
        }
        return Bytes.text(b, start, end);
    }

    private static String detectEol(byte[] b, int pos) {
        int i = pos;
        while (i < b.length) {
            if (b[i] == '\n') {
                return "\n";
            }
            if (b[i] == '\r') {
                return (i + 1 < b.length && b[i + 1] == '\n') ? "\r\n" : "\r";
            }
            i++;
        }
        for (int j = pos - 1; j >= 0; j--) {
            if (b[j] == '\r') {
                return (j + 1 < pos && b[j + 1] == '\n') ? "\r\n" : "\r";
            }
            if (b[j] == '\n') {
                return "\n";
            }
        }
        return "\n";
    }

    private static boolean isWsCp(int cp) {
        switch (cp) {
            case 0x09: case 0x0B: case 0x0C: case 0x20: case 0x85:
            case 0xA0: case 0x1680: case 0x2028: case 0x2029:
            case 0x202F: case 0x205F: case 0x3000:
                return true;
            default:
                return cp >= 0x2000 && cp <= 0x200A;
        }
    }

    private static final class Planned {
        final String body;
        final boolean desiredRaw;

        Planned(String body, boolean desiredRaw) {
            this.body = body;
            this.desiredRaw = desiredRaw;
        }
    }
}
