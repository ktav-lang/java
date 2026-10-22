package lang.ktav.pure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lang.ktav.Value;
/**
 * The formatter's emitter — the port of the Rust core's
 * {@code render::formatted}: canonical-structure emission of a
 * trivia-carrying {@link PureFormatter.FmtDoc}, flushing each trivia
 * run immediately before the construct it is attached to. Reuses the
 * canonical writer's rules unchanged (indent, key quoting, § 5.9.8
 * floats, § 5.9.7 string forms via {@link PureWriterText}); § 5.9.0
 * rejections surface as {@link PureWriter.Unrep} raised into the
 * caller's local buffer — never any partial output.
 *
 * <p><b>Iterative by construction.</b> Like {@link PureWriter}, emission
 * walks an explicit work stack of {@link Task}s — there is NO recursion
 * anywhere in this class and therefore NO depth-based guard or
 * rejection. That matters because the formatter's dotted-key expansion
 * (§ 5.6) materialises intermediate {@link PureFormatter.PObj} chains:
 * a document near {@code PureParser.MAX_DEPTH} in dotted depth nested
 * inside many open frames can exceed any single-frame guard, and would
 * be a spec-valid input. The stack holds one frame per compound level,
 * so the worst case is bounded by the document's own materialised
 * depth, and no spec-valid document is ever rejected. The decoded key
 * {@code path} is maintained exactly as in {@link PureWriter}: each
 * pair task pushes its key, a compound body queues exactly one
 * {@code POP_PATH} (popped after the body's children and closer,
 * LIFO), and leaf pairs pop in their own {@code finally}.
 */
final class PureFormatterEmitter {

    private PureFormatterEmitter() {
    }

    // ------------------------------------------------------------------
    // Emission — the port of render::formatted
    // ------------------------------------------------------------------

    static String emit(PureFormatter.FmtDoc doc) {
        StringBuilder out = new StringBuilder();
        emitTrivia(doc.leading, 0, out);
        ArrayList<String> path = new ArrayList<>();
        try {
            ArrayDeque<Task> work = new ArrayDeque<>();
            if (doc.root.obj != null) {
                // § 5.9.3: an Object root has NO closer — pairs at
                // indent 0.
                expandObjectBody(work, doc.root.obj, 0, true, false, 0);
            } else {
                expandArrayRoot(work, doc.root.arr);
            }
            while (!work.isEmpty()) {
                Task t = work.pop();
                switch (t.op) {
                    case PAIR -> processPair(work, t.key, t.value,
                            t.indent, t.flag, path, out);
                    case ITEM -> processItem(work, t.value, t.indent,
                            t.flag, path, out);
                    case TRIVIA -> emitTrivia(t.trivia, t.indent, out);
                    case LITERAL -> {
                        PureWriter.pushIndent(out, t.indent);
                        out.append(t.text);
                    }
                    case POP_PATH -> path.remove(path.size() - 1);
                }
            }
        } catch (PureWriter.Unrep e) {
            throw PureWriter.unrepresentable(e.code,
                    e.pathSnapshot != null ? e.pathSnapshot : path);
        }
        return out.toString();
    }

    /** One pending unit of work on the emission stack. */
    private enum Op {
        PAIR, ITEM, TRIVIA, LITERAL, POP_PATH
    }

    private static final class Task {
        final Op op;
        String key;
        PureFormatter.PNode value;
        int indent;
        boolean flag; // rootFirstKey / isRootArrayFirst
        String text; // LITERAL payload (emitted after `indent` spaces)
        List<PureFormatter.TriviaLine> trivia;

        private Task(Op op) {
            this.op = op;
        }

        static Task pair(String key, PureFormatter.PNode value, int indent,
                boolean rootFirstKey) {
            Task t = new Task(Op.PAIR);
            t.key = key;
            t.value = value;
            t.indent = indent;
            t.flag = rootFirstKey;
            return t;
        }

        static Task item(PureFormatter.PNode value, int indent,
                boolean isRootArrayFirst) {
            Task t = new Task(Op.ITEM);
            t.value = value;
            t.indent = indent;
            t.flag = isRootArrayFirst;
            return t;
        }

        static Task trivia(List<PureFormatter.TriviaLine> trivia,
                int indent) {
            Task t = new Task(Op.TRIVIA);
            t.trivia = trivia;
            t.indent = indent;
            return t;
        }

        static Task literal(String text, int indent) {
            Task t = new Task(Op.LITERAL);
            t.text = text;
            t.indent = indent;
            return t;
        }

        static Task popPath() {
            return new Task(Op.POP_PATH);
        }
    }

    private static void emitTrivia(List<PureFormatter.TriviaLine> trivia,
            int indent, StringBuilder out) {
        for (PureFormatter.TriviaLine line : trivia) {
            if (line.blank) {
                out.append('\n');
            } else {
                PureWriter.pushIndent(out, indent);
                out.append(line.text).append('\n');
            }
        }
    }

    // ------------------------------------------------------------------
    // Queueing — mirrors PureWriter's expand* helpers, plus trivia runs
    // ------------------------------------------------------------------

    /**
     * Queue an Object's pairs (insertion order), each preceded by its
     * slot's trivia run, then — when {@code close} — the {@code }}
     * closer at {@code closeIndent} (the PAIR's indent for a nested
     * body) and the compound's trailing trivia at the body indent. The
     * root Object passes {@code close=false} (§ 5.9.3: no root closer).
     */
    private static void expandObjectBody(ArrayDeque<Task> work,
            PureFormatter.PObj obj, int indent, boolean isRoot,
            boolean close, int closeIndent) {
        // Pushed first ⇒ popped last: trailing trivia follows the pairs.
        work.push(Task.trivia(obj.trailing, indent));
        if (close) {
            work.push(Task.literal("}\n", closeIndent));
        }
        int index = obj.pairs.size();
        for (Map.Entry<String, PureFormatter.Slot> e : obj.pairs
                .entrySet()) {
            index--;
            // Per entry the pop order must be trivia-then-pair, so push
            // the pair first and the trivia after it. Reverse iteration
            // (via the countdown above) preserves insertion order.
            work.push(Task.pair(e.getKey(), e.getValue().value, indent,
                    isRoot && index == 0));
            work.push(Task.trivia(e.getValue().trivia, indent));
        }
    }

    /**
     * Queue an Array's items (insertion order), each preceded by its
     * slot's trivia run, then the {@code ]} closer at {@code closeIndent}
     * and the trailing trivia at {@code indent}.
     */
    private static void expandArrayItems(ArrayDeque<Task> work,
            PureFormatter.PArr arr, int indent, boolean close,
            int closeIndent) {
        work.push(Task.trivia(arr.trailing, indent));
        if (close) {
            work.push(Task.literal("]\n", closeIndent));
        }
        for (int i = arr.items.size() - 1; i >= 0; i--) {
            PureFormatter.Slot slot = arr.items.get(i);
            work.push(Task.item(slot.value, indent, false));
            work.push(Task.trivia(slot.trivia, indent));
        }
    }

    /**
     * § 5.9.3 root-array wrap decision, unchanged from the recursive
     * version: an empty Array root is the explicit line {@code []};
     * otherwise wrap iff the first item is a compound (empty or not —
     * § 5.9.3's two hazard shapes) or only trailing trivia remains.
     */
    private static void expandArrayRoot(ArrayDeque<Task> work,
            PureFormatter.PArr arr) {
        if (arr.items.isEmpty() && arr.trailing.isEmpty()) {
            // The single-line root has no queued children; emit it
            // through a literal so ordering with the leading trivia
            // (already flushed) is trivially correct.
            work.push(Task.literal("[]\n", 0));
            return;
        }
        boolean firstIsCompound = !arr.items.isEmpty()
                && isCompound(arr.items.get(0).value);
        boolean needsWrap = firstIsCompound
                || (arr.items.isEmpty() && !arr.trailing.isEmpty());
        if (needsWrap) {
            // Items at indent 1, "]" closer at indent 0 (§ 5.9.3).
            work.push(Task.literal("]\n", 0));
            for (int i = arr.items.size() - 1; i >= 0; i--) {
                PureFormatter.Slot slot = arr.items.get(i);
                work.push(Task.item(slot.value, 1, false));
                work.push(Task.trivia(slot.trivia, 1));
            }
            work.push(Task.trivia(arr.trailing, 1));
        } else {
            // § 5.9.6 / § 5.9.12: only index 0 of the unwrapped root
            // Array is exposed to root-kind detection.
            work.push(Task.trivia(arr.trailing, 0));
            for (int i = arr.items.size() - 1; i >= 0; i--) {
                PureFormatter.Slot slot = arr.items.get(i);
                work.push(Task.item(slot.value, 0, i == 0));
                work.push(Task.trivia(slot.trivia, 0));
            }
        }
    }

    // ------------------------------------------------------------------
    // Task processing — canonical rules via PureWriterText
    // ------------------------------------------------------------------

    private static void processPair(ArrayDeque<Task> work, String key,
            PureFormatter.PNode value, int indent, boolean rootFirstKey,
            ArrayList<String> path, StringBuilder out) {
        // The key is on the path while the pair emits (and, for a
        // compound body, while the queued children run — they pop it via
        // the single queued POP_PATH, LIFO after the body's closer).
        path.add(key);
        boolean leaf = true;
        try {
            if (key.isEmpty()) {
                // § 5.9.0, before the key's first byte is pushed; the
                // snapshot names the offending pair's key.
                throw new PureWriter.Unrep("EmptyKeyName",
                        new ArrayList<>(path));
            }
            PureWriter.pushIndent(out, indent);
            PureWriter.pushEscapedKeySegment(key, rootFirstKey, out);
            if (value.scalar != null) {
                Value v = value.scalar;
                if (v instanceof Value.Null) {
                    out.append(": null\n");
                } else if (v instanceof Value.Bool b) {
                    out.append(": ").append(b.value() ? "true" : "false")
                            .append('\n');
                } else if (v instanceof Value.Int i) {
                    out.append(": ").append(i.text()).append('\n');
                } else if (v instanceof Value.Flt f) {
                    out.append(": ")
                            .append(PureWriter.canonicalFloatChecked(
                                    f.text()))
                            .append('\n');
                } else {
                    PureWriter.emitCanonicalStringInPair(
                            ((Value.Str) v).value(), indent, out);
                }
                return;
            }
            if (value.arr != null) {
                PureFormatter.PArr a = value.arr;
                if (a.items.isEmpty() && a.trailing.isEmpty()) {
                    out.append(": []\n");
                } else {
                    out.append(": [\n");
                    work.push(Task.popPath());
                    expandArrayItems(work, a, indent + 1, true, indent);
                    leaf = false; // POP_PATH owns the pop now
                }
            } else {
                PureFormatter.PObj o = value.obj;
                if (o.pairs.isEmpty() && o.trailing.isEmpty()) {
                    out.append(": {}\n");
                } else {
                    out.append(": {\n");
                    work.push(Task.popPath());
                    expandObjectBody(work, o, indent + 1, false, true,
                            indent);
                    leaf = false; // POP_PATH owns the pop now
                }
            }
        } catch (PureWriter.Unrep e) {
            // Re-raise keeping the DEEPEST path: a nested throw already
            // carries its own (longer) snapshot.
            throw new PureWriter.Unrep(e.code, e.pathSnapshot != null
                    ? e.pathSnapshot
                    : new ArrayList<>(path));
        } finally {
            if (leaf) {
                path.remove(path.size() - 1);
            }
        }
    }

    private static void processItem(ArrayDeque<Task> work,
            PureFormatter.PNode value, int indent,
            boolean isRootArrayFirst, ArrayList<String> path,
            StringBuilder out) {
        PureWriter.pushIndent(out, indent);
        if (value.scalar != null) {
            Value v = value.scalar;
            if (v instanceof Value.Null) {
                out.append("null\n");
            } else if (v instanceof Value.Bool b) {
                out.append(b.value() ? "true" : "false").append('\n');
            } else if (v instanceof Value.Int i) {
                out.append(i.text()).append('\n');
            } else if (v instanceof Value.Flt f) {
                out.append(PureWriter.canonicalFloatChecked(f.text()))
                        .append('\n');
            } else {
                PureWriter.emitCanonicalStringAsItem(
                        ((Value.Str) v).value(), indent, isRootArrayFirst,
                        out);
            }
            return;
        }
        if (value.arr != null) {
            PureFormatter.PArr a = value.arr;
            if (a.items.isEmpty() && a.trailing.isEmpty()) {
                out.append("[]\n");
            } else {
                out.append("[\n");
                // An array item's closer sits at the ITEM's indent
                // (§ 5.9.1: the compound's own indent).
                expandArrayItems(work, a, indent + 1, true, indent);
            }
        } else {
            PureFormatter.PObj o = value.obj;
            if (o.pairs.isEmpty() && o.trailing.isEmpty()) {
                out.append("{}\n");
            } else {
                out.append("{\n");
                expandObjectBody(work, o, indent + 1, false, true, indent);
            }
        }
    }

    private static boolean isCompound(PureFormatter.PNode n) {
        return n != null && (n.obj != null || n.arr != null);
    }
}
