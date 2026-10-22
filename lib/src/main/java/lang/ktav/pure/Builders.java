package lang.ktav.pure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import lang.ktav.Value;

/**
 * Mutable document builders. Object scopes use insertion-ordered
 * {@link LinkedHashMap}s; values inside a builder are a {@link Value}
 * leaf or a nested builder. Dotted-key resolution implements § 5.3.2:
 * reopen-merge of both synthetic and explicit sub-objects, plus both
 * conflict directions (§ 6.2 {@code DuplicateKey} vs § 6.3
 * {@code KeyPathConflict}), distinguished via the synthetic flag.
 */
final class Builders {

    private Builders() {
    }

    /** Lazy write slot for compound/multi-line values opened before completion. */
    interface Slot {
        void set(Object v);
    }

    static final class ObjBuilder {
        final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        /** True when created by dotted-key expansion (synthetic prefix). */
        boolean synthetic;
    }

    static final class ArrBuilder {
        final ArrayList<Object> items = new ArrayList<>();
    }

    /**
     * Resolve a dotted path in {@code root} for a LEAF assignment and
     * return the write slot. Intermediate names must resolve to (or
     * create) objects — an existing non-object intermediate is a
     * KeyPathConflict; an existing leaf name is a DuplicateKey, except
     * a plain (non-dotted) pair naming a synthetic object, which is the
     * reverse KeyPathConflict (§ 5.3.2). {@code plainKey} distinguishes
     * plain pairs from dotted-key leaves for that rule.
     */
    static Slot leafSlot(ObjBuilder root, Keys.RawPath key,
            boolean plainKey, int line,
            String lineText, int spanStart, int spanEnd) {
        List<String> fullPath = new ArrayList<>(key.size());
        ObjBuilder parent = walk(root, key, fullPath, line, lineText,
                spanStart, spanEnd);
        String last = fullPath.get(fullPath.size() - 1);
        Object existing = parent.map.get(last);
        if (existing != null) {
            if (plainKey && existing instanceof ObjBuilder b && b.synthetic) {
                throw conflict(fullPath, line, lineText, spanStart, spanEnd);
            }
            throw duplicate(fullPath, line, lineText, spanStart, spanEnd);
        }
        return v -> parent.map.put(last, v);
    }

    /**
     * Resolve a dotted path for a COMPOUND opener ({@code a.b: {}): the
     * leaf must not exist at all; a synthetic object there is a
     * KeyPathConflict (plain pair naming a dotted-established object),
     * anything else a DuplicateKey.
     */
    static Slot compoundSlot(ObjBuilder root, Keys.RawPath key,
            int line, String lineText,
            int spanStart, int spanEnd) {
        List<String> fullPath = new ArrayList<>(key.size());
        ObjBuilder parent = walk(root, key, fullPath, line, lineText,
                spanStart, spanEnd);
        String last = fullPath.get(fullPath.size() - 1);
        Object existing = parent.map.get(last);
        if (existing != null) {
            if (existing instanceof ObjBuilder b && b.synthetic) {
                throw conflict(fullPath, line, lineText, spanStart, spanEnd);
            }
            throw duplicate(fullPath, line, lineText, spanStart, spanEnd);
        }
        return v -> parent.map.put(last, v);
    }

    /**
     * Walk/create every intermediate object along {@code key} minus the
     * last, appending each VALIDATED, DECODED segment to
     * {@code decoded} as it is traversed (§ 5.3.2 / Rust
     * {@code build/insert.rs insert_dotted}): a blocked prefix reports
     * KeyPathConflict over the decoded prefix BEFORE the next segment's
     * escape defect can surface.
     */
    private static ObjBuilder walk(ObjBuilder root, Keys.RawPath key,
            List<String> decoded, int line, String lineText,
            int spanStart, int spanEnd) {
        if (key.size() > PureParser.MAX_DEPTH) {
            // Depth bound shared with compound nesting (declared limit).
            throw new SourceError(
                    "dotted key path exceeds " + PureParser.MAX_DEPTH
                            + " segments",
                    "Other", null, null, null, null, null, null, null,
                    null, null);
        }
        ObjBuilder cur = root;
        for (int i = 0; i < key.size() - 1; i++) {
            decoded.add(key.decode(i));
            String seg = decoded.get(i);
            Object existing = cur.map.get(seg);
            if (existing == null) {
                ObjBuilder created = new ObjBuilder();
                created.synthetic = true;
                cur.map.put(seg, created);
                cur = created;
            } else if (existing instanceof ObjBuilder b) {
                cur = b; // explicit objects merge too (§ 5.3.2)
            } else {
                // Envelope path = FULL raw-split with decode/raw
                // fallback (error/envelope.rs kind_path); the walk
                // decision above already used the decoded prefix only.
                throw conflict(key.diagnosticPath(), line, lineText,
                        spanStart, spanEnd);
            }
        }
        decoded.add(key.decode(key.size() - 1));
        return cur;
    }

    /** Append to an array, returning the slot that replaces the placeholder. */
    static Slot appendSlot(ArrBuilder arr) {
        int idx = arr.items.size();
        arr.items.add(null);
        return v -> arr.items.set(idx, v);
    }

    private static SourceError duplicate(List<String> path, int line,
            String lineText, int spanStart, int spanEnd) {
        return SourceError.withPath("DuplicateKey", "§6.2", line, lineText,
                spanStart, spanEnd, path,
                "DuplicateKey at line " + line + ": '" + path.get(path.size() - 1) + "'");
    }

    private static SourceError conflict(List<String> path, int line,
            String lineText, int spanStart, int spanEnd) {
        return SourceError.withPath("KeyPathConflict", "§6.3", line,
                lineText, spanStart, spanEnd, path,
                "KeyPathConflict at line " + line + ": '"
                        + String.join(".", path) + "'");
    }

    /** Convert a builder tree into the immutable {@link Value} model. */
    static Value toValue(Object o, int depth) {
        if (depth > PureParser.MAX_DEPTH) {
            throw new SourceError("value nesting exceeds " + PureParser.MAX_DEPTH,
                    "Other", null, null, null, null, null, null, null, null,
                    null);
        }
        if (o instanceof ObjBuilder b) {
            LinkedHashMap<String, Value> out = new LinkedHashMap<>();
            for (var en : b.map.entrySet()) {
                out.put(en.getKey(), toValue(en.getValue(), depth + 1));
            }
            return new Value.Obj(out);
        }
        if (o instanceof ArrBuilder b) {
            ArrayList<Value> out = new ArrayList<>(b.items.size());
            for (Object item : b.items) {
                out.add(toValue(item, depth + 1));
            }
            return new Value.Arr(out);
        }
        return (Value) o;
    }
}
