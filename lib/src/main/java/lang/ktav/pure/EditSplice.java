package lang.ktav.pure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import lang.ktav.KtavException;
import lang.ktav.Value;

/**
 * Source-splice engine for the pure-Java source edit — the behavioural
 * twin of the Rust core's {@code document/parser/edit/splice.rs} +
 * {@code splice/layout.rs}: replace only the target's bytes via the
 * positional tree ({@link EditTree}), preserve comments / whitespace /
 * key spelling / order / BOM / EOLs outside the effective edit, expand
 * inline wrappers only when the layout criteria demand it, and verify
 * the patch semantically (exactly the target replaced, rest of the tree
 * equal). A matching replacement is a byte-identical no-op.
 */
final class EditSplice {

    private EditSplice() {
    }

    // ── Public entry ────────────────────────────────────────────────────

    static String insert(byte[] b, int bomStart, Value original, List<String> segments, Value replacement) {
        if (segments.isEmpty()) throw EditError.notFound();
        List<String> parentPath = segments.subList(0, segments.size() - 1);
        String key = segments.get(segments.size() - 1);
        Value parent = lookupValue(original, parentPath);
        if (parent instanceof Value.Arr) throw EditError.unsupported(
                "insertion into an Array is not supported; only Object members can be created");
        if (!(parent instanceof Value.Obj object)) throw EditError.notFound();
        if (object.entries().containsKey(key)) throw EditError.alreadyExists();

        EditTree.Result tree = EditTree.build(b);
        Descend parentLoc = parentPath.isEmpty()
                ? new Descend(tree.root, tree.root.inline, false)
                : descend(tree.root, parentPath, tree.rootExplicit);
        if (parentLoc.node.slot == null && !parentPath.isEmpty())
            throw EditError.unsupported("parent is a noncontiguous value (dotted-key/reopen intermediate); no lossless insertion point exists");
        EditTree.Node node = parentLoc.node;
        if (node.kind != EditTree.Node.OBJECT) throw EditError.notFound();
        String result;
        int layoutFrom = -1;
        int layoutTo = -1;
        int layoutInserted = 0;
        boolean layoutEligible = false;

        boolean owningInlineBody = node.slot != null
                && (node.slot.kind == EditTree.Slot.PAIR || node.slot.kind == EditTree.Slot.ITEM)
                && isInlineBody(b, node.slot.b0, node.slot.b1);
        if (node.inline || owningInlineBody) {
            int[] trimmed;
            if (node.slot != null) {
                trimmed = Bytes.trim(b, node.slot.b0, node.slot.b1);
            } else if (parentPath.isEmpty()) {
                int lineStart = bomStart;
                int contentStart = -1;
                while (lineStart < b.length) {
                    int lineEnd = lineStart;
                    while (lineEnd < b.length && b[lineEnd] != '\n' && b[lineEnd] != '\r') lineEnd++;
                    int[] content = Bytes.trim(b, lineStart, lineEnd);
                    if (content[0] < content[1] && !(content[1] - content[0] >= 2
                            && b[content[0]] == '#' && b[content[0] + 1] == '#')) {
                        contentStart = content[0];
                        break;
                    }
                    lineStart = lineEnd;
                    while (lineStart < b.length && (b[lineStart] == '\n' || b[lineStart] == '\r')) lineStart++;
                }
                if (contentStart < 0) throw EditError.unsupported("inline Object has no lossless insertion range");
                int lineEnd = contentStart;
                while (lineEnd < b.length && b[lineEnd] != '\n' && b[lineEnd] != '\r') lineEnd++;
                trimmed = Bytes.trim(b, contentStart, lineEnd);
            } else {
                throw EditError.unsupported("inline Object has no lossless insertion range");
            }
            int end = trimmed[1];
            if (end <= trimmed[0] || b[trimmed[0]] != '{' || b[end - 1] != '}')
                throw EditError.unsupported("inline Object has no lossless insertion range");
            String existingInline = Bytes.text(b, trimmed[0], end);
            if (existingInline.indexOf('\n') >= 0 || existingInline.indexOf('\r') >= 0)
                throw EditError.unsupported("multiline inline Object insertion is unsupported");
            String eol = detectEol(b, trimmed[1]);
            int close = end - 1;
            int[] trailing = Bytes.trim(b, trimmed[0] + 1, close);
            int insertAt = trailing[1];
            boolean trailingComma = insertAt > trimmed[0] + 1 && b[insertAt - 1] == ',';
            int slashCount = 0;
            if (trailingComma) {
                for (int i = insertAt - 2; i >= trimmed[0] + 1 && b[i] == '\\'; i--) slashCount++;
                trailingComma = (slashCount & 1) == 0;
            }
            boolean existingMembers = hasInlineMember(b, trimmed[0] + 1,
                    trailingComma ? insertAt - 1 : close);
            String inlinePair = encodeInsertKeySegment(key) + ": " + inlineBody(replacement, true);
            int insertedAt;
            String insertion;
            int spliceTo;
            if (!hasInlineMember(b, trimmed[0] + 1, close)) {
                insertedAt = trimmed[0] + 1;
                spliceTo = close;
                insertion = inlinePair;
            } else {
                if (trailingComma) {
                    insertedAt = insertAt;
                    spliceTo = insertedAt;
                    insertion = " " + inlinePair + ",";
                } else {
                    insertedAt = close;
                    spliceTo = insertedAt;
                    insertion = (existingMembers ? ", " : (hasInlineWhitespace(b, trimmed[0] + 1, close) ? " " : "")) + inlinePair;
                }
            }
            result = Bytes.text(b, 0, insertedAt) + insertion
                    + Bytes.text(b, spliceTo, b.length);
            layoutFrom = insertedAt;
            layoutTo = spliceTo;
            layoutInserted = insertion.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            layoutEligible = true;
        } else if (node.slot != null && node.slot.kind == EditTree.Slot.BLOCK) {
            int closeLine = lineStart(b, node.slot.b1 - 1);
            String indent = lineIndent(b, node.slot.b0);
            String childIndent = siblingIndent(b, node);
            if (childIndent == null) childIndent = indent + "    ";
            String eol = detectEol(b, node.slot.b1);
            String blockPair = standalonePair(key, replacement, childIndent, eol);
            int insertAt = closeLine;
            String prefix = insertAt > 0 && b[insertAt - 1] != '\n'
                    && b[insertAt - 1] != '\r' ? eol : "";
            result = Bytes.text(b, 0, insertAt) + prefix + childIndent + blockPair + eol
                    + Bytes.text(b, insertAt, b.length);
        } else if (parentPath.isEmpty() && tree.rootExplicit) {
            int at = tree.rootClose;
            if (at < bomStart || at >= b.length || b[at] != '}')
                throw EditError.unsupported("explicit root Object has no recorded closing brace");
            String childIndent = node.entries.isEmpty() ? "    " : siblingIndent(b, node);
            if (childIndent == null) childIndent = "    ";
            String eol = detectEol(b, at);
            String blockPair = standalonePair(key, replacement, childIndent, eol);
            int insertAt = lineStart(b, at);
            if (insertAt < bomStart) insertAt = at;
            String prefix = insertAt > bomStart && b[insertAt - 1] != '\n'
                    && b[insertAt - 1] != '\r' ? eol : "";
            result = Bytes.text(b, 0, insertAt) + prefix + childIndent + blockPair + eol
                    + Bytes.text(b, insertAt, b.length);
        } else {
            int at = b.length;
            String eol = detectEol(b, b.length);
            String blockPair = standalonePair(key, replacement, "", eol);
            boolean hasFinalEol = at > 0 && (b[at - 1] == '\n' || b[at - 1] == '\r');
            String addition;
            if (at == bomStart && bomStart > 0) {
                addition = eol + blockPair;
            } else if (at == 0) {
                addition = blockPair;
            } else if (hasFinalEol) {
                addition = blockPair + eol;
            } else {
                addition = eol + blockPair;
            }
            result = Bytes.text(b, 0, at) + addition;
        }

        byte[] rb = result.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Value reparsed;
        try { reparsed = PureParser.parse(rb, bomStart, false); }
        catch (SourceError e) { throw EditError.unsupported("patch produced an invalid document: " + e.getMessage()); }
        LinkedHashMap<String, Value> expectedMap = new LinkedHashMap<>(object.entries());
        expectedMap.put(key, replacement);
        Value expected = replaceAt(original, parentPath, new Value.Obj(expectedMap));
        if (!expected.equals(reparsed)) throw EditError.unsupported("semantic mismatch after insertion");
        if (layoutEligible) {
            String adjusted;
            try {
                adjusted = Layout.adjust(b, result, tree.root, parentPath, replacement,
                        layoutFrom, layoutTo, layoutInserted);
            } catch (EditError layoutFailure) {
                return result;
            }
            if (!adjusted.equals(result)) {
                byte[] ab = adjusted.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try {
                    Value laidOut = PureParser.parse(ab, bomStart, false);
                    if (expected.equals(laidOut)) result = adjusted;
                } catch (SourceError ignored) {
                    // Preserve the already-verified inline insertion candidate.
                }
            }
        }
        return result;
    }

    private static boolean isInlineBody(byte[] b, int from, int to) {
        int[] body = Bytes.trim(b, from, to);
        return body[1] - body[0] >= 2 && b[body[0]] == '{' && b[body[1] - 1] == '}';
    }

    private static String standalonePair(String key, Value value, String indent, String eol) {
        String encodedKey = encodeInsertKeySegment(key);
        boolean compound = value instanceof Value.Obj || value instanceof Value.Arr;
        boolean emptyCompound = value instanceof Value.Obj o && o.entries().isEmpty()
                || value instanceof Value.Arr a && a.items().isEmpty();
        if (!compound || emptyCompound) {
            PlanCtx pc = new PlanCtx();
            pc.slot = EditTree.Slot.PAIR;
            pc.roleItemBlock = false;
            pc.existingRaw = false;
            pc.rootFirst = false;
            pc.indent = indent;
            pc.eol = eol;
            pc.inlineCompound = false;
            pc.blockCompound = true;
            Planned planned = planValue(value, pc);
            String marker = planned.desiredRaw ? "::" : ":";
            return encodedKey + marker + " " + planned.body;
        }
        LinkedHashMap<String, Value> one = new LinkedHashMap<>();
        one.put(key, value);
        final String rendered;
        try {
            rendered = PureWriter.dumps(new Value.Obj(one));
        } catch (KtavException e) {
            throw EditError.unsupported("replacement is not representable: " + e.getMessage());
        }
        String normalized = rendered;
        if (normalized.endsWith("\n")) normalized = normalized.substring(0, normalized.length() - 1);
        String out = reindentInsert(normalized, indent, eol);
        return out;
    }

    private static String siblingIndent(byte[] b, EditTree.Node node) {
        if (node.kind != EditTree.Node.OBJECT || node.entries.isEmpty()) return null;
        EditTree.Node last = null;
        for (EditTree.Node child : node.entries.values()) last = child;
        if (last == null || last.slot == null) return null;
        int pos = last.slot.kind == EditTree.Slot.BLOCK ? last.slot.b0
                : last.slot.kind == EditTree.Slot.PAIR ? last.slot.anchor : last.slot.anchor;
        return lineIndent(b, pos);
    }

    private static Value replaceAt(Value root, List<String> path, Value value) {
        if (path.isEmpty()) return value;
        String head = path.get(0);
        List<String> rest = path.subList(1, path.size());
        if (root instanceof Value.Obj o) {
            LinkedHashMap<String, Value> entries = new LinkedHashMap<>(o.entries());
            Value child = entries.get(head);
            if (child == null) throw EditError.notFound();
            entries.put(head, replaceAt(child, rest, value));
            return new Value.Obj(entries);
        }
        if (root instanceof Value.Arr a) {
            int index = parseIndex(head);
            if (index >= a.items().size()) throw EditError.notFound();
            List<Value> items = new ArrayList<>(a.items());
            items.set(index, replaceAt(items.get(index), rest, value));
            return new Value.Arr(items);
        }
        throw EditError.notFound();
    }

    private static int lineStart(byte[] b, int pos) {
        int i = Math.min(pos, b.length);
        while (i > 0 && b[i - 1] != '\n' && b[i - 1] != '\r') i--;
        return i;
    }

    private static boolean hasInlineWhitespace(byte[] b, int from, int to) {
        for (int i = from; i < to; ) {
            int cp = b[i] & 0xff;
            int len = cp < 0x80 ? 1 : cp < 0xe0 ? 2 : cp < 0xf0 ? 3 : 4;
            if (i + len > to) return false;
            String unit = new String(b, i, len, java.nio.charset.StandardCharsets.UTF_8);
            if (unit.codePointCount(0, unit.length()) != 1 || !isKtavWhitespace(unit.codePointAt(0))) return false;
            i += len;
        }
        return to > from;
    }

    private static boolean isKtavWhitespace(int cp) {
        return cp == 0x09 || cp == 0x0b || cp == 0x0c || cp == 0x20
                || cp == 0x85 || cp == 0xa0 || cp == 0x1680
                || cp >= 0x2000 && cp <= 0x200a || cp == 0x2028
                || cp == 0x2029 || cp == 0x202f || cp == 0x205f
                || cp == 0x3000;
    }

    private static boolean hasInlineMember(byte[] b, int from, int to) {
        return Bytes.trim(b, from, to)[0] < Bytes.trim(b, from, to)[1];
    }
    /**
     * Replace the value addressed by {@code segments} in validated
     * source {@code b} (whose grammar begins at {@code bomStart}) with
     * {@code replacement}. Returns the patched source text; the input is
     * never mutated.
     */
    static String apply(byte[] b, int bomStart, List<String> segments,
            Value replacement) {
        Value originalValue = PureParser.parse(b, bomStart, false);
        Value target = lookupValue(originalValue, segments);
        if (target == null) {
            throw EditError.notFound();
        }
        if (target.equals(replacement)) {
            return Bytes.text(b, 0, b.length);
        }

        EditTree.Result tree = EditTree.build(b);
        Descend d = descend(tree.root, segments, tree.rootExplicit);
        EditTree.Node node = d.node;

        EditTree.Slot slot = node.slot;
        if (slot == null) {
            throw EditError.unsupported(
                    "target is a noncontiguous value (dotted-key/reopen"
                            + " intermediate); no lossless single-range splice"
                            + " exists");
        }

        int slotKind = slot.kind;
        int a;        // anchor of the marker zone
        int b0 = slot.b0;
        int b1 = slot.b1;
        boolean blockItemRole = false;
        switch (slotKind) {
            case EditTree.Slot.PAIR -> a = slot.anchor;
            case EditTree.Slot.ITEM -> a = slot.anchor;
            default -> {
                a = slot.anchor >= 0 ? slot.anchor : b0;
                blockItemRole = slot.anchor < 0;
            }
        }
        if (a > b1 || b0 > b1 || a < 0) {
            throw EditError.unsupported("internal: invalid marker/body offsets");
        }

        boolean inlineItem = d.inline && slotKind == EditTree.Slot.ITEM;
        boolean existingRaw;
        String wsRaw = "";
        String wsPlain = "";
        switch (slotKind) {
            case EditTree.Slot.BLOCK -> existingRaw = false;
            default -> {
                if (inlineItem) {
                    existingRaw = false;
                } else {
                    existingRaw = b[a + 1] == ':';
                    if (existingRaw) {
                        if (a + 2 > b0) {
                            throw EditError.unsupported(
                                    "internal: invalid raw marker range");
                        }
                        wsRaw = Bytes.text(b, a + 2, b0);
                    }
                    if (slotKind == EditTree.Slot.PAIR) {
                        if (a + 1 > b0) {
                            throw EditError.unsupported(
                                    "internal: invalid plain marker range");
                        }
                        wsPlain = Bytes.text(b, a + 1, b0);
                    }
                }
            }
        }

        // Structural rewrite of a container must not lose interior
        // comment lines; multiline scalar blocks are data, not markup.
        if (node.kind == EditTree.Node.OBJECT || node.kind == EditTree.Node.ARRAY) {
            List<int[]> ranges = new ArrayList<>();
            collectMultilineScalarRanges(node, ranges);
            if (hasCommentLine(b, b0, b1, ranges)) {
                throw EditError.unsupported(
                        "target container contains comment lines; structural"
                                + " replacement cannot preserve them losslessly");
            }
        }

        String indent = lineIndent(b, a);
        String eol = detectEol(b, b1);
        PlanCtx pc = new PlanCtx();
        pc.slot = slotKind;
        pc.roleItemBlock = slotKind == EditTree.Slot.BLOCK && blockItemRole;
        pc.existingRaw = existingRaw;
        pc.rootFirst = d.rootFirst;
        pc.indent = indent;
        pc.eol = eol;
        pc.inlineCompound = slotKind != EditTree.Slot.BLOCK
                && (node.kind == EditTree.Node.OBJECT
                        || node.kind == EditTree.Node.ARRAY);
        pc.blockCompound = slotKind == EditTree.Slot.BLOCK
                && (node.kind == EditTree.Node.OBJECT
                        || node.kind == EditTree.Node.ARRAY);

        String wsPairBlock = (slotKind == EditTree.Slot.BLOCK && !blockItemRole)
                ? Bytes.text(b, a + 1, b0)
                : "";

        Plan plan;
        if (d.inline) {
            if (slotKind == EditTree.Slot.ITEM
                    && replacement instanceof Value.Str s && s.value().isEmpty()) {
                plan = new Plan(b0, "", "", "()");
            } else if (slotKind == EditTree.Slot.ITEM) {
                plan = new Plan(b0, "", "", inlineBody(replacement, true));
            } else if (existingRaw && !(replacement instanceof Value.Str)) {
                plan = new Plan(a + 1, wsRaw, "",
                        inlineBody(replacement, false));
            } else {
                plan = new Plan(b0, "", "",
                        inlineBody(replacement, !existingRaw));
            }
        } else if (slotKind == EditTree.Slot.BLOCK) {
            plan = blockReplacement(replacement, pc, a, b0, wsPairBlock);
        } else {
            Planned planned = planValue(replacement, pc);
            if (existingRaw == planned.desiredRaw) {
                String prefix = (b0 == b1 && wsPlain.isEmpty() && wsRaw.isEmpty())
                        ? " " : "";
                plan = new Plan(b0, prefix, "", planned.body);
            } else if (existingRaw && slotKind == EditTree.Slot.PAIR) {
                plan = new Plan(a + 1, sepWs(b0, b1, wsRaw), "", planned.body);
            } else if (existingRaw) {
                plan = new Plan(a, wsRaw, "", planned.body);
            } else if (slotKind == EditTree.Slot.PAIR) {
                plan = new Plan(a + 1, ":", sepWs(b0, b1, wsPlain),
                        planned.body);
            } else {
                plan = new Plan(a, ":: ", "", planned.body);
            }
        }

        int from = plan.from;
        if (from > b1) {
            throw EditError.unsupported("internal: splice range out of bounds");
        }
        String result = Bytes.text(b, 0, from) + plan.prefix0 + plan.prefix1
                + plan.body + Bytes.text(b, b1, b.length);

        result = Layout.adjust(b, result, tree.root, segments, replacement,
                from, b1, insertedBytes(plan));

        // Obligatory semantic verification: reparse and check that
        // exactly the target was replaced.
        byte[] rb = result.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Value newDoc;
        try {
            newDoc = PureParser.parse(rb, bomStart, false);
        } catch (SourceError e) {
            throw EditError.unsupported(
                    "patch produced an invalid document: " + e.getMessage());
        }
        boolean targetMatches;
        try {
            Value actual = lookupValue(newDoc, segments);
            targetMatches = actual != null && actual.equals(replacement);
        } catch (EditError e) {
            targetMatches = false;
        }
        Value expected = nullTargets(originalValue, segments);
        Value actual = nullTargets(newDoc, segments);
        if (!targetMatches || !actual.equals(expected)) {
            throw EditError.unsupported(
                    "semantic mismatch after splice; no lossless patch"
                            + " produced");
        }
        return result;
    }

    /** Insertion size in UTF-8 BYTES — Layout works in byte space. */
    private static int insertedBytes(Plan plan) {
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
        return plan.prefix0.getBytes(utf8).length
                + plan.prefix1.getBytes(utf8).length
                + plan.body.getBytes(utf8).length;
    }

    private static final class Plan {
        final int from;
        final String prefix0;
        final String prefix1;
        final String body;

        Plan(int from, String prefix0, String prefix1, String body) {
            this.from = from;
            this.prefix0 = prefix0;
            this.prefix1 = prefix1;
            this.body = body;
        }
    }

    private static final class PlanCtx {
        int slot;
        boolean roleItemBlock;
        boolean existingRaw;
        boolean rootFirst;
        String indent;
        String eol;
        boolean inlineCompound;
        boolean blockCompound;
    }

    private static final class Planned {
        final String body;
        final boolean desiredRaw;

        Planned(String body, boolean desiredRaw) {
            this.body = body;
            this.desiredRaw = desiredRaw;
        }
    }

    // ── Path helpers ────────────────────────────────────────────────────

    static final class Descend {
        final EditTree.Node node;
        final boolean inline;
        final boolean rootFirst;

        Descend(EditTree.Node node, boolean inline, boolean rootFirst) {
            this.node = node;
            this.inline = inline;
            this.rootFirst = rootFirst;
        }
    }

    private static Descend descend(EditTree.Node root, List<String> segments,
            boolean rootExplicit) {
        boolean containerIsRoot = true;
        EditTree.Node cur = root;
        boolean rootFirst = false;
        for (String seg : segments) {
            if (cur.kind == EditTree.Node.OBJECT) {
                rootFirst = false;
                cur = cur.child(seg);
                if (cur == null) {
                    throw EditError.notFound();
                }
            } else if (cur.kind == EditTree.Node.ARRAY) {
                int idx = parseIndex(seg);
                rootFirst = containerIsRoot && !rootExplicit && idx == 0;
                cur = cur.item(idx);
                if (cur == null) {
                    throw EditError.notFound();
                }
            } else {
                throw EditError.notFound();
            }
            containerIsRoot = false;
        }
        return new Descend(cur, cur.inline, rootFirst);
    }

    static Value lookupValue(Value v, List<String> segments) {
        Value cur = v;
        for (String seg : segments) {
            if (cur instanceof Value.Obj o) {
                cur = o.entries().get(seg);
            } else if (cur instanceof Value.Arr arr) {
                int idx = parseIndexOrNull(seg);
                cur = idx < 0 ? null
                        : (idx < arr.items().size() ? arr.items().get(idx)
                                : null);
            } else {
                return null;
            }
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }

    /** Copy of {@code v} with the value at {@code segments} replaced by null. */
    static Value nullTargets(Value v, List<String> segments) {
        if (segments.isEmpty()) {
            return v;
        }
        String head = segments.get(0);
        List<String> rest = segments.subList(1, segments.size());
        boolean last = rest.isEmpty();
        if (v instanceof Value.Obj o) {
            LinkedHashMap<String, Value> out = new LinkedHashMap<>();
            o.entries().forEach((k, val) -> out.put(k,
                    k.equals(head)
                            ? (last ? Value.Null.NULL : nullTargets(val, rest))
                            : val));
            return new Value.Obj(out);
        }
        if (v instanceof Value.Arr arr) {
            int idx = parseIndexOrNull(head);
            List<Value> out = new ArrayList<>(arr.items().size());
            for (int i = 0; i < arr.items().size(); i++) {
                out.add(i == idx
                        ? (last ? Value.Null.NULL
                                : nullTargets(arr.items().get(i), rest))
                        : arr.items().get(i));
            }
            return new Value.Arr(out);
        }
        return v;
    }

    /** ASCII digits only; leading zeros allowed, signs are not indexes. */
    static int parseIndex(String seg) {
        int idx = parseIndexOrNull(seg);
        if (idx < 0) {
            throw EditError.notFound();
        }
        return idx;
    }

    private static int parseIndexOrNull(String seg) {
        if (seg.isEmpty()) {
            return -1;
        }
        long acc = 0;
        for (int i = 0; i < seg.length(); i++) {
            char c = seg.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            acc = acc * 10 + (c - '0');
            if (acc > Integer.MAX_VALUE) {
                return -1; // out of bounds anyway
            }
        }
        return (int) acc;
    }

    // ── Comment detection ───────────────────────────────────────────────

    private static void collectMultilineScalarRanges(EditTree.Node node,
            List<int[]> out) {
        if (node.kind == EditTree.Node.SCALAR && node.slot != null
                && node.slot.kind == EditTree.Slot.BLOCK) {
            out.add(new int[] { node.slot.b0, node.slot.b1 });
            return;
        }
        if (node.kind == EditTree.Node.OBJECT) {
            for (EditTree.Node child : node.entries.values()) {
                collectMultilineScalarRanges(child, out);
            }
        } else if (node.kind == EditTree.Node.ARRAY) {
            for (EditTree.Node child : node.items) {
                collectMultilineScalarRanges(child, out);
            }
        }
    }

    /** Any line in [from, to) starting (after § 3.3 ws) with '##' outside skip ranges. */
    private static boolean hasCommentLine(byte[] b, int from, int to,
            List<int[]> skip) {
        skip.sort((x, y) -> Integer.compare(x[0], y[0]));
        int skipI = 0;
        int i = from;
        while (i < to) {
            int j = i;
            while (j < to && b[j] != '\n' && b[j] != '\r') {
                j++;
            }
            int[] t = Bytes.trim(b, i, j);
            while (skipI < skip.size() && skip.get(skipI)[1] <= t[0]) {
                skipI++;
            }
            boolean inData = false;
            if (skipI < skip.size() && skip.get(skipI)[0] <= t[0]
                    && t[0] < skip.get(skipI)[1]) {
                inData = true;
            }
            if (!inData && t[1] - t[0] >= 2 && b[t[0]] == '#'
                    && b[t[0] + 1] == '#') {
                return true;
            }
            i = j;
            while (i < to && (b[i] == '\n' || b[i] == '\r')) {
                i++;
            }
        }
        return false;
    }

    // ── Body encoding ───────────────────────────────────────────────────

    private static String scalarBody(Value v) {
        if (v == Value.Null.NULL) {
            return "null";
        }
        if (v instanceof Value.Bool bo) {
            return bo.value() ? "true" : "false";
        }
        if (v instanceof Value.Int i) {
            return i.text();
        }
        if (v instanceof Value.Flt f) {
            return f.text();
        }
        throw EditError.unsupported(
                "internal: scalar_body called on a compound value");
    }

    private static String inlineBody(Value replacement, boolean forceString) {
        if (replacement instanceof Value.Str s) {
            return encodeInline(s.value(), forceString && needsRawMarker(s.value()));
        }
        if (replacement instanceof Value.Obj || replacement instanceof Value.Arr) {
            StringBuilder out = new StringBuilder();
            pushInlineValue(replacement, 0, out);
            return out.toString();
        }
        return scalarBody(replacement);
    }

    static String encodeInline(String s, boolean forceString) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        pushInlineString(s, forceString, out);
        return out.toString();
    }

    private static void pushInlineString(String s, boolean forceString,
            StringBuilder out) {
        int last = s.isEmpty() ? 0 : lastCodePointStart(s);
        int i = 0;
        boolean first = true;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (forceString && first) {
                out.append(String.format("\\u%04X", cp));
            } else {
                encodeInlineChar(out, cp, first || i == last);
            }
            first = false;
            i += Character.charCount(cp);
        }
    }

    private static int lastCodePointStart(String s) {
        int i = s.length() - 1;
        while (i > 0 && Character.isLowSurrogate(s.charAt(i))) {
            i--;
        }
        return i;
    }

    private static void encodeInlineChar(StringBuilder out, int cp,
            boolean edge) {
        switch (cp) {
            case '\\' -> out.append("\\\\");
            case ',' -> out.append("\\,");
            case '}' -> out.append("\\}");
            case ']' -> out.append("\\]");
            case '{' -> out.append("\\{");
            case '[' -> out.append("\\[");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            default -> {
                if (edge && isWsCp(cp)) {
                    out.append(String.format("\\u%04X", cp));
                } else {
                    out.appendCodePoint(cp);
                }
            }
        }
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

    static void appendInlineValue(Value v, StringBuilder out) {
        pushInlineValue(v, 0, out);
    }

    private static void pushInlineValue(Value v, int depth, StringBuilder out) {
        if (depth > PureParser.MAX_DEPTH) {
            throw EditError.unsupported(
                    "replacement nesting exceeds inline depth limit ("
                            + PureParser.MAX_DEPTH + ")");
        }
        if (v instanceof Value.Obj o) {
            out.append('{');
            boolean first = true;
            for (var e : o.entries().entrySet()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(encodeKeySegment(e.getKey()));
                out.append(": ");
                if (e.getValue() instanceof Value.Str s) {
                    pushInlineString(s.value(), needsRawMarker(s.value()), out);
                } else {
                    pushInlineValue(e.getValue(), depth + 1, out);
                }
            }
            out.append('}');
        } else if (v instanceof Value.Arr arr) {
            out.append('[');
            boolean first = true;
            for (Value item : arr.items()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                if (item instanceof Value.Str s && s.value().isEmpty()) {
                    out.append("()");
                } else if (item instanceof Value.Str s) {
                    pushInlineString(s.value(), needsRawMarker(s.value()), out);
                } else {
                    pushInlineValue(item, depth + 1, out);
                }
            }
            out.append(']');
        } else if (v instanceof Value.Str s) {
            pushInlineString(s.value(), needsRawMarker(s.value()), out);
        } else {
            out.append(scalarBody(v));
        }
    }

    private static String encodeInsertKeySegment(String key) {
        if (key.isEmpty()) throw EditError.unsupported("empty key name is not representable");
        StringBuilder out = new StringBuilder();
        PureWriter.pushEscapedKeySegment(key, false, out);
        return out.toString();
    }

    /** § 5.9.10 key emission for inline compounds: bare, else quoted+escaped. */
    private static String encodeKeySegment(String key) {
        StringBuilder out = new StringBuilder();
        pushEscapedKeySegment(key, false, out);
        return out.toString();
    }

    private static void pushEscapedKeySegment(String key, boolean rootFirst,
            StringBuilder out) {
        if (isBareKeyCandidate(key, rootFirst)) {
            out.append(key);
            return;
        }
        // Quoted form with re-escaping; double quotes preferred.
        out.append('"');
        for (int i = 0; i < key.length(); ) {
            int cp = key.codePointAt(i);
            if (cp == '"' || cp == '\\') {
                out.append('\\').appendCodePoint(cp);
            } else if (cp == '\n') {
                out.append("\\n");
            } else if (cp == '\r') {
                out.append("\\r");
            } else if (cp == '\t') {
                out.append("\\t");
            } else {
                out.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        out.append('"');
    }

    /**
     * A bare key is non-empty § 4 key text: no whitespace edges, no
     * structural characters, no dotted/quoted look-alikes. The full
     * parser is the oracle; this predicate is conservative — anything
     * questionable is quoted.
     */
    private static boolean isBareKeyCandidate(String key, boolean rootFirst) {
        if (key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); ) {
            int cp = key.codePointAt(i);
            if (isWsCp(cp) || Character.isISOControl(cp)) {
                return false;
            }
            switch (cp) {
                case ':': case '.': case ',': case '{': case '}':
                case '[': case ']': case '(': case ')': case '"':
                case '\'': case '`': case '\\': case '#':
                    return false;
                default:
                    i += Character.charCount(cp);
            }
        }
        // Leading BOM cannot appear bare (root-dispatch protection).
        return key.codePointAt(0) != 0xFEFF;
    }

    // ── Planning ────────────────────────────────────────────────────────

    private static String sepWs(int b0, int b1, String ws) {
        return (b0 == b1 && ws.isEmpty()) ? " " : ws;
    }

    private static Planned planValue(Value replacement, PlanCtx pc) {
        if (replacement instanceof Value.Str s) {
            return planString(s.value(), pc);
        }
        if (replacement instanceof Value.Obj || replacement instanceof Value.Arr) {
            if (pc.inlineCompound) {
                StringBuilder out = new StringBuilder();
                pushInlineValue(replacement, 0, out);
                return new Planned(out.toString(), false);
            }
            if (replacement instanceof Value.Obj o && o.entries().isEmpty()) {
                return new Planned(pc.blockCompound
                        ? "{" + pc.eol + pc.indent + "}" : "{}", false);
            }
            if (replacement instanceof Value.Arr a && a.items().isEmpty()) {
                return new Planned(pc.blockCompound
                        ? "[" + pc.eol + pc.indent + "]" : "[]", false);
            }
            String rendered;
            try {
                rendered = PureWriter.dumps(replacement);
            } catch (KtavException e) {
                throw EditError.unsupported(
                        "replacement is not representable: " + e.getMessage());
            }
            return new Planned(reindent(rendered, pc.indent, pc.eol), false);
        }
        return new Planned(scalarBody(replacement), false);
    }

    private static Planned planString(String s, PlanCtx pc) {
        if (s.indexOf('\r') >= 0) {
            throw EditError.unsupported(
                    "replacement string contains CR (U+000D)");
        }
        boolean edgeWs = (!s.isEmpty() && isWsCp(s.codePointAt(0)))
                || (!s.isEmpty() && isWsCp(s.codePointBefore(s.length())));
        if (s.indexOf('\n') >= 0 || edgeWs) {
            return new Planned(verbatimBlockBody(s, pc.indent, pc.eol), false);
        }
        boolean desiredRaw;
        if (pc.slot == EditTree.Slot.ITEM) {
            desiredRaw = itemNeedsRawMarker(s) || s.isEmpty();
        } else {
            desiredRaw = needsRawMarker(s);
        }
        if (pc.slot == EditTree.Slot.ITEM && pc.rootFirst) {
            desiredRaw = desiredRaw || bareItemIsPairCandidate(s)
                    || s.startsWith("\uFEFF");
        }
        if (pc.existingRaw) {
            desiredRaw = true;
        }
        return new Planned(s, desiredRaw);
    }

    private static String verbatimBlockBody(String s, String indent,
            String eol) {
        for (String line : s.split("\n", -1)) {
            if (trim(line).equals("))")) {
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

    private static String trim(String s) {
        int b = 0;
        int e = s.length();
        while (b < e && isWsCp(s.codePointAt(b))) {
            b += Character.charCount(s.codePointAt(b));
        }
        while (e > b && isWsCp(s.codePointBefore(e))) {
            e = s.offsetByCodePoints(e, -1);
        }
        return s.substring(b, e);
    }

    private static Plan blockReplacement(Value replacement, PlanCtx pc,
            int a, int b0, String wsPair) {
        if (replacement instanceof Value.Str s) {
            String sv = s.value();
            if (sv.indexOf('\r') >= 0) {
                throw EditError.unsupported(
                        "replacement string contains CR (U+000D)");
            }
            boolean multiline = sv.indexOf('\n') >= 0
                    || (!sv.isEmpty() && isWsCp(sv.codePointAt(0)))
                    || (!sv.isEmpty() && isWsCp(sv.codePointBefore(sv.length())));
            if (multiline) {
                return new Plan(b0, "", "",
                        verbatimBlockBody(sv, pc.indent, pc.eol));
            }
            if (pc.roleItemBlock && sv.isEmpty()) {
                return new Plan(b0, "", "", "()");
            }
            boolean itemRaw = pc.roleItemBlock
                    && (itemNeedsRawMarker(sv)
                            || (pc.rootFirst
                                    && (bareItemIsPairCandidate(sv)
                                            || sv.startsWith("\uFEFF"))));
            boolean pairRaw = !pc.roleItemBlock && needsRawMarker(sv);
            if (itemRaw) {
                return new Plan(b0, ":: ", "", sv);
            }
            if (pairRaw) {
                return new Plan(a + 1, ":", wsPair, sv);
            }
            return new Plan(b0, "", "", sv);
        }
        PlanCtx sub = pc;
        sub.slot = EditTree.Slot.BLOCK;
        Planned p = planValue(replacement, sub);
        return new Plan(b0, "", "", p.body);
    }

    /** Pretty render re-indented under the target's indent; verbatim block interiors untouched. */
    private static String reindent(String doc, String indent, String eol) {
        StringBuilder out = new StringBuilder(doc.length());
        boolean inVerbatim = false;
        String[] lines = doc.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("    ")) {
                line = line.substring(4);
            }
            boolean verbatimOpen = line.endsWith("((");
            boolean verbatimClose = trim(line).equals("))");
            if (i > 0) {
                out.append(eol);
                boolean skipPrefix = inVerbatim || line.isEmpty();
                if (!skipPrefix) {
                    out.append(indent);
                }
            }
            out.append(line);
            if (verbatimOpen) {
                inVerbatim = true;
            } else if (verbatimClose) {
                inVerbatim = false;
            }
        }
        return out.toString();
    }

    // ── Line helpers ────────────────────────────────────────────────────

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
                // CRLF: the LF's preceding CR makes the terminator CRLF.
                return (j > 0 && b[j - 1] == '\r') ? "\r\n" : "\n";
            }
        }
        return "\n";
    }

    // ── Raw-marker predicates (mirror render/helpers.rs) ────────────────

    static boolean itemNeedsRawMarker(String s) {
        return needsRawMarker(s) || s.startsWith("##") || s.startsWith("::")
                || s.equals("]") || s.equals("}");
    }

    static boolean bareItemIsPairCandidate(String body) {
        byte[] kb = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Keys.ColonResult cr = Keys.scanColon(kb, 0, kb.length);
        if (cr.kind != Keys.Colon.FOUND) {
            return false;
        }
        int ci = cr.index;
        int[] pre = Bytes.trim(kb, 0, ci);
        if (pre[0] >= pre[1]) {
            return false;
        }
        if (ci + 1 < kb.length && kb[ci + 1] == ':') {
            return true;
        }
        return ci + 1 >= kb.length || Bytes.wsAt(kb, ci + 1, kb.length) > 0;
    }

    static boolean needsRawMarker(String s) {
        if (s.isEmpty()) {
            return false;
        }
        char c0 = s.charAt(0);
        if (c0 == ' ' || c0 == '\t') {
            String t = s.stripLeading();
            return t.startsWith("{") || t.startsWith("[")
                    || needsRawMarkerContent(t);
        }
        if (c0 == '{' || c0 == '[') {
            return true;
        }
        return needsRawMarkerContent(s);
    }

    private static boolean needsRawMarkerContent(String s) {
        if (s.equals("null") || s.equals("true") || s.equals("false")
                || s.equals("(") || s.equals("((") || s.equals("()")
                || s.equals("(())")) {
            return true;
        }
        return matchesIntegerGrammar(s) || matchesFloatGrammar(s);
    }

    /** § 3.6 integer grammar match (grammar-level, NOT bounded to i64). */
    static boolean matchesIntegerGrammar(String s) {
        int n = s.length();
        int i = 0;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        if (i >= n) {
            return false;
        }
        if (s.charAt(i) == '0' && i + 1 < n) {
            switch (s.charAt(i + 1)) {
                case 'x' -> {
                    return validDigitRun(s, i + 2, 16);
                }
                case 'o' -> {
                    return validDigitRun(s, i + 2, 8);
                }
                case 'b' -> {
                    return validDigitRun(s, i + 2, 2);
                }
                default -> {
                }
            }
        }
        return validDigitRun(s, i, 10);
    }

    private static boolean validDigitRun(String s, int from, int radix) {
        int n = s.length();
        if (from >= n) {
            return false;
        }
        if (!digitFor(s.charAt(from), radix)) {
            return false;
        }
        boolean prevUnderscore = false;
        for (int i = from; i < n; i++) {
            char c = s.charAt(i);
            if (c == '_') {
                if (prevUnderscore) {
                    return false;
                }
                prevUnderscore = true;
                continue;
            }
            if (!digitFor(c, radix)) {
                return false;
            }
            prevUnderscore = false;
        }
        return !prevUnderscore;
    }

    private static boolean digitFor(char c, int radix) {
        int v;
        if (c >= '0' && c <= '9') {
            v = c - '0';
        } else if (c >= 'a' && c <= 'f') {
            v = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
            v = c - 'A' + 10;
        } else {
            return false;
        }
        return v < radix;
    }

    /** § 3.6 float grammar match. */
    static boolean matchesFloatGrammar(String s) {
        int n = s.length();
        if (n == 0) {
            return false;
        }
        char c0 = s.charAt(0);
        if (!(c0 >= '0' && c0 <= '9') && c0 != '+' && c0 != '-') {
            return false;
        }
        if (s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) {
            return false;
        }
        int i = 0;
        if (s.charAt(i) == '+' || s.charAt(i) == '-') {
            i++;
        }
        int[] r = scanDecPart(s, i);
        if (r[1] == 0) {
            return false;
        }
        i = r[0];
        if (i < n && s.charAt(i) == '.') {
            i++;
            r = scanDecPart(s, i);
            if (r[1] == 0) {
                return false;
            }
            i = r[0];
            if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                r = scanExponent(s, i);
                if (r[1] == 0) {
                    return false;
                }
                i = r[0];
            }
            return i == n;
        }
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            r = scanExponent(s, i);
            if (r[1] == 0) {
                return false;
            }
            return r[0] == n;
        }
        return false;
    }

    private static int[] scanDecPart(String s, int from) {
        int n = s.length();
        if (from >= n || !Character.isDigit(s.charAt(from))
                || s.charAt(from) > '9') {
            return new int[] { from, 0 };
        }
        boolean prevUnderscore = false;
        int i = from;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '_') {
                if (prevUnderscore) {
                    return new int[] { i, 0 };
                }
                prevUnderscore = true;
                i++;
                continue;
            }
            if (c < '0' || c > '9') {
                break;
            }
            prevUnderscore = false;
            i++;
        }
        if (prevUnderscore) {
            return new int[] { i, 0 };
        }
        return new int[] { i, 1 };
    }

    private static int[] scanExponent(String s, int from) {
        int n = s.length();
        int i = from + 1; // skip 'e'/'E'
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        return scanDecPart(s, i);
    }

    private static String reindentInsert(String doc, String indent, String eol) {
        StringBuilder out = new StringBuilder(doc.length());
        boolean inVerbatim = false;
        String[] lines = doc.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int leading = 0;
            while (leading < line.length() && line.charAt(leading) == ' ') leading++;
            String trimmed = line.substring(leading);
            boolean closingMarker = inVerbatim && trim(line).equals("))");
            boolean opens = !inVerbatim && trimmed.endsWith("((");
            if (i > 0) out.append(eol);
            if (closingMarker) {
                out.append(indent).append(line);
                inVerbatim = false;
            } else if (inVerbatim) {
                out.append(line);
            } else {
                if (i == 0) out.append(line);
                else out.append(indent).append(line);
                if (opens) inVerbatim = true;
            }
        }
        return out.toString();
    }
}
