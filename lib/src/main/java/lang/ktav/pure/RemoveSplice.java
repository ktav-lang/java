package lang.ktav.pure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import lang.ktav.Value;

/** Lossless source splices for removing existing values. */
final class RemoveSplice {
    private RemoveSplice() { }

    static String apply(byte[] source, int bomStart, List<String> segments) {
        Value original = PureParser.parse(source, bomStart, false);
        if (segments.isEmpty()) throw EditError.notFound();
        EditTree.Result tree = EditTree.build(source);
        Parent parent = parent(original, segments);
        EditTree.Node parentNode = descend(tree.root, segments.subList(0, segments.size() - 1));
        String leaf = segments.get(segments.size() - 1);
        EditTree.Node target;
        int index = -1;
        if (parent.value instanceof Value.Obj object) {
            if (!object.entries().containsKey(leaf)) throw EditError.notFound();
            target = parentNode.entries.get(leaf);
        } else if (parent.value instanceof Value.Arr array) {
            index = arrayIndex(leaf, array.items().size());
            target = parentNode.items.get(index);
        } else throw EditError.notFound();
        if (target == null || target.slot == null)
            throw EditError.unsupported("target is a noncontiguous value; no lossless removal range exists");
        int from, to;
        boolean inlineParent = target.inline;
        if (inlineParent) {
            int[] range = inlineRange(source, parentNode, target, tree, segments.size() == 1);
            from = range[0]; to = range[1];
        } else {
            from = lineStart(source, target.slot.kind == EditTree.Slot.BLOCK
                    ? target.slot.b0 : target.slot.anchor);
            if (from == 0) from = bomStart;
            to = target.slot.kind == EditTree.Slot.BLOCK
                    ? target.slot.b1 : lineEnd(source, target.slot.b1);
            if (to < source.length && source[to] == '\r') {
                to++; if (to < source.length && source[to] == '\n') to++;
            } else if (to < source.length && source[to] == '\n') to++;
            if (to == source.length && from < to
                    && source[to - 1] != '\n' && source[to - 1] != '\r') {
                if (from > 0 && source[from - 1] == '\n') {
                    from--; if (from > 0 && source[from - 1] == '\r') from--;
                } else if (from > 0 && source[from - 1] == '\r') from--;
            }
        }
        if (target.kind == EditTree.Node.OBJECT || target.kind == EditTree.Node.ARRAY) {
            List<int[]> ranges = new ArrayList<>();
            collectMultilineScalarRanges(target, ranges);
            if (hasCommentLine(source, from, to, ranges))
                throw EditError.unsupported("target contains comment lines; removal cannot preserve them losslessly");
        }
        String result = Bytes.text(source, 0, from) + Bytes.text(source, to, source.length);
        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
        Value parsed = null;
        try { parsed = PureParser.parse(bytes, bomStart, false); }
        catch (SourceError ignored) { /* try no-prune materialization below */ }
        Value expected = removeAt(original, segments, index);
        if (expected.equals(parsed)) return result;
        String materialized = materializeEmptyDotted(source, from, to, segments, expected, tree.root,
                bomStart);
        if (materialized == null) throw EditError.unsupported("semantic mismatch after removal");
        result = materialized;
        bytes = result.getBytes(StandardCharsets.UTF_8);
        try { parsed = PureParser.parse(bytes, bomStart, false); }
        catch (SourceError e) { throw EditError.unsupported("patch produced an invalid document: " + e.getMessage()); }
        if (!expected.equals(parsed)) throw EditError.unsupported("semantic mismatch after removal");
        return result;
    }

    private static String materializeEmptyDotted(byte[] source, int from, int to,
                                                   List<String> path, Value expected,
                                                   EditTree.Node root, int bomStart) {
        if (path.size() < 2 || to <= from) return null;
        Value v = expected;
        for (int i = 0; i < path.size() - 1; i++) {
            if (v instanceof Value.Obj o) v = o.entries().get(path.get(i));
            else if (v instanceof Value.Arr a) {
                int index;
                try { index = arrayIndex(path.get(i), a.items().size()); }
                catch (EditError e) { return null; }
                v = a.items().get(index);
            } else return null;
            if (v == null) return null;
        }
        if (!(v instanceof Value.Obj o) || !o.entries().isEmpty()) return null;
        int emptyAt = -1;
        for (int i = 0; i < path.size() - 1; i++) {
            if (isEmptyObjectAt(expected, path, i + 1)) { emptyAt = i; break; }
        }
        if (emptyAt < 0) return null;
        int containerDepth = 0;
        EditTree.Node cursor = root;
        for (int i = 0; i < path.size() - 1; i++) {
            String segment = path.get(i);
            if (cursor.kind == EditTree.Node.OBJECT) cursor = cursor.entries.get(segment);
            else if (cursor.kind == EditTree.Node.ARRAY) {
                int index;
                try { index = arrayIndex(segment, cursor.items.size()); }
                catch (EditError e) { return null; }
                cursor = cursor.items.get(index);
            } else break;
            if (cursor == null) break;
            if (cursor.slot != null) containerDepth = i + 1;
        }
        if (emptyAt < containerDepth) return null;
        int leadLen = from < source.length && source[from] == '\r'
                ? (from + 1 < source.length && source[from + 1] == '\n' ? 2 : 1)
                : from < source.length && source[from] == '\n' ? 1 : 0;
        int lineStartOff = from + leadLen;
        int indentEnd = lineStartOff;
        while (indentEnd < to && Bytes.wsAt(source, indentEnd, to) > 0)
            indentEnd += Bytes.wsAt(source, indentEnd, to);
        String indent = Bytes.text(source, lineStartOff, indentEnd);
        StringBuilder key = new StringBuilder();
        for (int i = containerDepth; i <= path.size() - 2; i++) {
            if (i > containerDepth) key.append('.');
            PureWriter.pushEscapedKeySegment(path.get(i), false, key);
        }
        String suffix = Bytes.text(source, to, source.length);
        String ending = "";
        if (to > from && (source[to - 1] == '\n' || source[to - 1] == '\r')) {
            if (source[to - 1] == '\n' && to >= 2 && source[to - 2] == '\r') ending = "\r\n";
            else ending = source[to - 1] == '\n' ? "\n" : "\r";
        }
        return Bytes.text(source, 0, from) + Bytes.text(source, from, lineStartOff)
                + indent + key + ": {}" + ending + suffix;
    }

    private static boolean isEmptyObjectAt(Value root, List<String> path, int depth) {
        Value value = root;
        for (int i = 0; i < depth; i++) {
            String segment = path.get(i);
            if (value instanceof Value.Obj object) value = object.entries().get(segment);
            else if (value instanceof Value.Arr array) {
                try { value = array.items().get(arrayIndex(segment, array.items().size())); }
                catch (EditError e) { return false; }
            } else return false;
            if (value == null) return false;
        }
        return value instanceof Value.Obj object && object.entries().isEmpty();
    }

    private static int[] inlineRange(byte[] source, EditTree.Node parent, EditTree.Node target,
                                     EditTree.Result tree, boolean root) {
        int p0, p1;
        if (root) {
            int anchorLine = lineStart(source, target.slot.anchor);
            int anchorEnd = lineEnd(source, target.slot.anchor);
            int[] trimmed = Bytes.trim(source, anchorLine, anchorEnd);
            p0 = trimmed[0]; p1 = trimmed[1];
            if (p0 + 2 < p1 && source[p0] == (byte) 0xef
                    && source[p0 + 1] == (byte) 0xbb && source[p0 + 2] == (byte) 0xbf) p0 += 3;
        } else {
            if (parent.slot == null)
                throw EditError.unsupported("inline parent range unavailable");
            p0 = parent.slot.b0; p1 = parent.slot.b1;
        }
        int anchor = target.slot.anchor;
        int open = p0;
        while (open < p1 && source[open] != '{' && source[open] != '[') open++;
        if (open == p1) throw EditError.unsupported("inline parent range unavailable");
        int close = p1 - 1;
        List<int[]> spans = EditTree.splitTopLevel(source, open + 1, close, source[open] == '{');
        for (int[] span : spans) {
            int[] trimmed = Bytes.trim(source, span[0], span[1]);
            span[0] = trimmed[0]; span[1] = trimmed[1];
        }
        int pos = -1; for (int i=0;i<spans.size();i++) if (spans.get(i)[0] <= anchor && anchor < spans.get(i)[1]) {pos=i;break;}
        if (pos < 0) throw EditError.unsupported("target inline segment not found");
        if (spans.size() == 1) return new int[]{open+1, close};
        return pos + 1 < spans.size() ? new int[]{spans.get(pos)[0], spans.get(pos+1)[0]} : new int[]{spans.get(pos-1)[1], spans.get(pos)[1]};
    }
    private static void addSpan(byte[] b, List<int[]> spans, int a, int z) {
        int[] trimmed = Bytes.trim(b, a, z);
        if (trimmed[0] < trimmed[1]) spans.add(new int[]{trimmed[0], trimmed[1]});
    }
    private static void collectMultilineScalarRanges(EditTree.Node node, List<int[]> out) {
        if (node.kind == EditTree.Node.SCALAR && node.slot != null
                && node.slot.kind == EditTree.Slot.BLOCK) {
            out.add(new int[]{node.slot.b0, node.slot.b1});
            return;
        }
        if (node.kind == EditTree.Node.OBJECT) {
            for (EditTree.Node child : node.entries.values()) collectMultilineScalarRanges(child, out);
        } else if (node.kind == EditTree.Node.ARRAY) {
            for (EditTree.Node child : node.items) collectMultilineScalarRanges(child, out);
        }
    }

    private static boolean hasCommentLine(byte[] b, int from, int to, List<int[]> skip) {
        skip.sort((x, y) -> Integer.compare(x[0], y[0]));
        int skipI = 0;
        for (int i = from; i < to;) {
            int j = i;
            while (j < to && b[j] != '\n' && b[j] != '\r') j++;
            int[] t = Bytes.trim(b, i, j);
            while (skipI < skip.size() && skip.get(skipI)[1] <= t[0]) skipI++;
            boolean inData = skipI < skip.size() && skip.get(skipI)[0] <= t[0]
                    && t[0] < skip.get(skipI)[1];
            if (!inData && t[1] - t[0] >= 2 && b[t[0]] == '#' && b[t[0] + 1] == '#') return true;
            i = j;
            while (i < to && (b[i] == '\n' || b[i] == '\r')) i++;
        }
        return false;
    }

    private static final class Parent { final Value value; Parent(Value value) { this.value = value; } }
    private static Parent parent(Value root, List<String> path) {
        Value v = root;
        for (int i = 0; i + 1 < path.size(); i++) {
            String s = path.get(i);
            if (v instanceof Value.Obj o) v = o.entries().get(s);
            else if (v instanceof Value.Arr a) { int n = arrayIndex(s, a.items().size()); v = a.items().get(n); }
            else throw EditError.notFound();
            if (v == null) throw EditError.notFound();
        }
        return new Parent(v);
    }
    private static int arrayIndex(String s, int size) {
        if (s.isEmpty() || !s.chars().allMatch(c -> c >= '0' && c <= '9')) throw EditError.notFound();
        try { int i = Integer.parseInt(s); if (i < 0 || i >= size) throw EditError.notFound(); return i; }
        catch (NumberFormatException e) { throw EditError.notFound(); }
    }
    private static EditTree.Node descend(EditTree.Node n, List<String> path) {
        for (String s : path) {
            if (n.kind == EditTree.Node.OBJECT) n = n.entries.get(s);
            else if (n.kind == EditTree.Node.ARRAY) { int i = arrayIndex(s, n.items.size()); n = n.items.get(i); }
            else throw EditError.notFound();
            if (n == null) throw EditError.notFound();
        }
        return n;
    }
    private static Value removeAt(Value root, List<String> path, int arrayIndex) {
        if (path.size() == 1) {
            if (root instanceof Value.Obj o) { LinkedHashMap<String, Value> m = new LinkedHashMap<>(o.entries()); m.remove(path.get(0)); return new Value.Obj(m); }
            if (root instanceof Value.Arr a) { int i = arrayIndex(path.get(0), a.items().size()); List<Value> l = new ArrayList<>(a.items()); l.remove(i); return new Value.Arr(l); }
            throw EditError.notFound();
        }
        String h = path.get(0); List<String> rest = path.subList(1, path.size());
        if (root instanceof Value.Obj o) { LinkedHashMap<String, Value> m = new LinkedHashMap<>(o.entries()); Value c = m.get(h); if (c == null) throw EditError.notFound(); m.put(h, removeAt(c, rest, -1)); return new Value.Obj(m); }
        if (root instanceof Value.Arr a) { int i = arrayIndex(h, a.items().size()); List<Value> l = new ArrayList<>(a.items()); l.set(i, removeAt(l.get(i), rest, -1)); return new Value.Arr(l); }
        throw EditError.notFound();
    }
    private static int lineStart(byte[] b, int p) { while (p > 0 && b[p-1] != '\n' && b[p-1] != '\r') p--; return p; }
    private static int lineEnd(byte[] b, int p) { while (p < b.length && b[p] != '\n' && b[p] != '\r') p++; return p; }
}
