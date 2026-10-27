package lang.ktav.pure;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lang.ktav.Value;

/** Lossless source splice for renaming one Object member. */
final class RenameSplice {
    private RenameSplice() { }

    static String apply(byte[] source, int bomStart, Value original, List<String> segments, String newKey) {
        if (segments.isEmpty()) throw EditError.notFound();
        if (newKey.isEmpty()) throw EditError.unsupported("empty key name is not representable");
        String oldKey = segments.get(segments.size() - 1);
        Value parent = original;
        EditTree.Result tree = EditTree.build(source);
        EditTree.Node node = tree.root;
        for (int i = 0; i < segments.size() - 1; i++) {
            String s = segments.get(i);
            if (parent instanceof Value.Obj o) parent = o.entries().get(s);
            else if (parent instanceof Value.Arr a) parent = a.items().get(index(s, a.items().size()));
            else throw EditError.notFound();
            if (parent == null) throw EditError.notFound();
            node = node.kind == EditTree.Node.OBJECT ? node.entries.get(s) : node.kind == EditTree.Node.ARRAY ? node.items.get(index(s, node.items.size())) : null;
            if (node == null) throw EditError.notFound();
        }
        if (parent instanceof Value.Arr) throw EditError.unsupported("rename of an Array element is not supported; only Object keys can be renamed");
        if (!(parent instanceof Value.Obj object) || !object.entries().containsKey(oldKey)) throw EditError.notFound();
        if (oldKey.equals(newKey)) return Bytes.text(source, 0, source.length);
        if (object.entries().containsKey(newKey)) throw EditError.alreadyExists();
        EditTree.Node target = node.entries.get(oldKey);
        if (target == null) throw EditError.notFound();
        if (target.slot == null) throw EditError.unsupported("target is a noncontiguous value (dotted-key/reopen intermediate); no lossless rename range exists");
        if (target.slot.kind == EditTree.Slot.ITEM || target.slot.kind == EditTree.Slot.BLOCK && target.slot.anchor < 0)
            throw EditError.unsupported("rename of an Array element is not supported; only Object keys can be renamed");
        int colon = target.slot.anchor;
        int[] keyRange = pairKeyRange(source, colon);
        int keyStart = keyRange[0], keyEnd = keyRange[1];
        if (keyStart >= keyEnd) throw EditError.unsupported("internal: empty key range");
        List<int[]> rawSegments = Keys.splitPath(source, keyStart, keyEnd);
        int[] lastSegment = rawSegments.get(rawSegments.size() - 1);
        int segFrom = lastSegment[0], segTo = lastSegment[1];
        int[] trimmed = Bytes.trim(source, segFrom, segTo);
        segFrom = trimmed[0]; segTo = trimmed[1];
        boolean rootFirst = segments.size() == 1 && node.entries.keySet().iterator().next().equals(oldKey);
        StringBuilder encoded = new StringBuilder();
        PureWriter.pushEscapedKeySegment(newKey, rootFirst, encoded);
        String result = Bytes.text(source, 0, segFrom) + encoded + Bytes.text(source, segTo, source.length);
        Value reparsed;
        try { reparsed = PureParser.parse(result.getBytes(StandardCharsets.UTF_8), bomStart, false); }
        catch (SourceError e) { throw EditError.unsupported("patch produced an invalid document: " + e.getMessage()); }
        LinkedHashMap<String, Value> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, Value> entry : object.entries().entrySet()) renamed.put(entry.getKey().equals(oldKey) ? newKey : entry.getKey(), entry.getValue());
        Value expected = replaceParent(original, segments.subList(0, segments.size() - 1), new Value.Obj(renamed));
        if (!expected.equals(reparsed)) throw EditError.unsupported("semantic mismatch after rename");
        return result;
    }
    private static Value replaceParent(Value root, List<String> path, Value replacement) {
        if (path.isEmpty()) return replacement;
        String h = path.get(0); List<String> rest = path.subList(1, path.size());
        if (root instanceof Value.Obj o) { LinkedHashMap<String, Value> m = new LinkedHashMap<>(o.entries()); Value c=m.get(h); if(c==null) throw EditError.notFound(); m.put(h, replaceParent(c,rest,replacement)); return new Value.Obj(m); }
        if (root instanceof Value.Arr a) { int i=index(h,a.items().size()); java.util.ArrayList<Value> l=new java.util.ArrayList<>(a.items()); l.set(i,replaceParent(l.get(i),rest,replacement)); return new Value.Arr(l); }
        throw EditError.notFound();
    }
    private static int index(String s, int size) { if(s.isEmpty() || !s.chars().allMatch(c -> c>='0'&&c<='9')) throw EditError.notFound(); try { int i=Integer.parseInt(s); if(i>=size) throw EditError.notFound(); return i; } catch(NumberFormatException e){ throw EditError.notFound(); } }
    private static int[] pairKeyRange(byte[] b, int colon) {
        int start = lineStart(b, colon);
        if (start == 0 && b.length >= 3 && (b[0] & 0xFF) == 0xEF
                && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) start = 3;
        int end = Bytes.trimEnd(b, start, colon);
        int boundary = start;
        int i = start;
        boolean segmentStart = true;
        while (i < end) {
            if (segmentStart) {
                i = Bytes.skipWs(b, i, end);
                if (i < end && (b[i] == '"' || b[i] == '\'' || b[i] == '`')) {
                    byte quote = b[i++];
                    while (i < end) {
                        if (b[i] == '\\') { i = Math.min(end, i + 2); continue; }
                        if (b[i++] == quote) break;
                    }
                    segmentStart = false;
                    continue;
                }
                segmentStart = false;
            }
            byte c = b[i];
            if (c == '\\') { i = Math.min(end, i + 2); continue; }
            if (c == '.' || c == ',' || c == '{' || c == '[') {
                boundary = i + 1;
                segmentStart = true;
            }
            i++;
        }
        return Bytes.trim(b, boundary, end);
    }
    private static int lineStart(byte[] b, int p) {
        while (p > 0 && b[p - 1] != '\n' && b[p - 1] != '\r') p--;
        return p;
    }
}
