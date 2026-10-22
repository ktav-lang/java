package lang.ktav.pure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lang.ktav.KtavException;
import lang.ktav.Value;
import lang.ktav.internal.ErrorEnvelope;

/**
 * The pure writer: plain ({@code dumps}), force-strings
 * ({@code toStringForceStrings}) and canonical ({@code emitCanonical})
 * rendering of a {@link Value} tree — a line-for-line behavioural port
 * of the Rust core's {@code document::render} module.
 *
 * <p>Layout conventions (matching the reference writers' documented
 * layout; canonical emission is additionally byte-pinned against the
 * native backend by {@code CrossBackendTest}):
 * <ul>
 *   <li>4-space indent per level; LF-only line endings; trailing LF at
 *       end of document; empty Object root → zero bytes; empty Array
 *       root → {@code []\n} (§ 5.9.3).</li>
 *   <li>Key order is NEVER changed — the emitters walk insertion order
 *       (§ 5.9.2); the canonical form is NOT convenience-sorted.</li>
 *   <li>Top-level Array wraps in {@code [ / ]} iff its first item is an
 *       Object or Array (§ 5.9.3 widened per the Rust core's fix for
 *       § 5.0.1 rules 2/3).</li>
 *   <li>Keys: § 5.9.10 bare/quoted form selection with rules (a)–(d)
 *       (structural bytes, edge whitespace, leading quote, root-first
 *       U+FEFF, leading {@code ##}); bare re-escape and {@code "-quoted
 *       re-escape recipes; U+XXXX escapes with uppercase hex.</li>
 *   <li>Strings: § 5.9.7 form selection — empty pair body {@code key:}
 *       / empty item {@code ::}; one-line bodies via the {@code ::}
 *       raw marker exactly when the parser would re-classify them
 *       (§ 5.2 keywords / number grammar / compound openers / multi-line
 *       openers / item-context collisions); multi-line bodies prefer
 *       verbatim {@code ((…))} in canonical form (body at indent 0) and
 *       indented stripped {@code (…)} in plain/force form (the Rust
 *       pretty writers' historical preference).</li>
 *   <li>Force-strings: every leaf scalar renders through the String
 *       path on its textual form ({@code null}/{@code true}/
 *       {@code false}/integer text/float text), exactly the core's
 *       flag-driven coercion, so {@code NonFiniteFloat} cannot fire;
 *       {@code ScalarRoot} still applies.</li>
 *   <li>Plain {@code dumps} emits the Float payload verbatim after the
 *       § 5.9.0 non-finite check; the canonical writer re-renders it
 *       through § 5.9.8 (shortest round-trip digits; scientific iff
 *       abs ≥ 1e7 or &lt; 1e-2; zero → {@code 0.0}/-0.0, unparseable
 *       payloads pass through).</li>
 * </ul>
 *
 * <p>§ 5.9.0 rejections are {@code UnrepresentableAt} envelopes with the
 * exact reason codes {@code ScalarRoot}, {@code EmptyKeyName},
 * {@code NonFiniteFloat}, {@code CRByte}, {@code BothFormsRequired},
 * {@code TrailingWhitespaceCollision},
 * {@code LeadingWhitespaceCollision}. Like the Rust writers, emission
 * runs into a LOCAL buffer that is discarded on rejection — no partial
 * output is ever observable — and the offending node is named by the
 * decoded key path maintained during emission (document order, root
 * kind decided first).
 */
final class PureWriter {

    private PureWriter() {
        this.canonical = false;
    }

    // ------------------------------------------------------------------
    // Public entry points (called from PureKtav)
    // ------------------------------------------------------------------

    /** Plain Ktav rendering — the core's {@code render}. */
    static String dumps(Value root) {
        return writeDocument(root, Mode.PLAIN);
    }

    /** Every leaf scalar coerced to a String — the core's {@code to_string_force_strings}. */
    static String toStringForceStrings(Value root) {
        return writeDocument(root, Mode.FORCE);
    }

    /** Spec § 5.9 canonical form — the core's {@code emit_canonical}. */
    static String emitCanonical(Value root) {
        return writeDocument(root, Mode.CANONICAL);
    }

    private enum Mode {
        PLAIN, FORCE, CANONICAL
    }

    /**
     * Internal writer rejection: a § 5.9.0 reason code without a path.
     * The emitters raise this freely; the document-level driver catches
     * it and attaches the current decoded key path (the core's
     * {@code attach_path} cold-path re-walk, collapsed into the live
     * path stack — same node, same path, one walk).
     */
    static final class Unrep extends RuntimeException {
        private static final long serialVersionUID = 1L;

        final String code;
        /**
         * Path snapshot at the throw site, or {@code null} when the
         * thrower had no live path — the document driver then uses its
         * own stack. Pair-level throws snapshot WITH the offending key
         * (the core's {@code attach_path} names the pair's key, and the
         * pair's {@code finally} has already popped it off the live
         * stack by the time the driver sees the exception).
         */
        final List<String> pathSnapshot;

        Unrep(String code) {
            this(code, null);
        }

        Unrep(String code, List<String> pathSnapshot) {
            super(code);
            this.code = code;
            this.pathSnapshot = pathSnapshot;
        }
    }

    private Mode mode;
    private final boolean canonical;
    private final ArrayList<String> path = new ArrayList<>();

    private PureWriter(Mode mode) {
        this.mode = mode;
        this.canonical = mode == Mode.CANONICAL;
    }

    private static String writeDocument(Value root, Mode mode) {
        // § 5.9.0's fixed precedence: the root-kind constraint is
        // decided before any node is visited; the path is empty.
        if (!(root instanceof Value.Obj) && !(root instanceof Value.Arr)) {
            throw unrepresentable("ScalarRoot", List.of());
        }
        PureWriter w = new PureWriter(mode);
        // Amortized-linear buffer growth; no recursive size pre-pass
        // (it would recurse unboundedly on programmatically deep
        // trees — the emission walk below is iterative for the same
        // reason).
        StringBuilder out = new StringBuilder(64);
        try {
            w.emitRoot(root, out);
        } catch (Unrep e) {
            throw unrepresentable(e.code,
                    e.pathSnapshot != null ? e.pathSnapshot : w.path);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Root / compound emission — ITERATIVE explicit-stack traversal
    // ------------------------------------------------------------------

    private enum Op {
        PAIR, ITEM, LITERAL, POP_PATH
    }

    /** One pending unit of work on the emission stack. */
    private static final class Task {
        final Op op;
        String key;
        Value value;
        int indent;
        boolean flag; // rootFirstKey / isRootArrayFirst
        String text; // LITERAL payload (emitted after `indent` spaces)

        private Task(Op op) {
            this.op = op;
        }

        static Task pair(String key, Value value, int indent,
                boolean rootFirstKey) {
            Task t = new Task(Op.PAIR);
            t.key = key;
            t.value = value;
            t.indent = indent;
            t.flag = rootFirstKey;
            return t;
        }

        static Task item(Value value, int indent,
                boolean isRootArrayFirst) {
            Task t = new Task(Op.ITEM);
            t.value = value;
            t.indent = indent;
            t.flag = isRootArrayFirst;
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

    private void emitRoot(Value root, StringBuilder out) {
        ArrayDeque<Task> work = new ArrayDeque<>();
        if (root instanceof Value.Obj o) {
            // § 5.9.3: an Object root has NO closer — pairs at indent 0.
            expandObjectBody(work, o, 0, true, false, 0);
        } else {
            List<Value> items = ((Value.Arr) root).items();
            if (items.isEmpty()) {
                // § 5.9.3: an empty Array root must be explicit, else it
                // would emit nothing and re-parse as an empty Object.
                out.append("[]\n");
                return;
            }
            if (firstItemNeedsWrap(items.get(0))) {
                out.append("[\n");
                // § 5.9.3: items at indent 1, "]" closer at indent 0.
                expandArray(work, items, 1, true, 0, true);
            } else {
                // § 5.9.6 / § 5.9.12: only index 0 of the unwrapped
                // root Array is exposed to root-kind detection.
                expandArray(work, items, 0, false, 0, true);
            }
        }
        while (!work.isEmpty()) {
            Task t = work.pop();
            switch (t.op) {
                case PAIR -> processPair(work, t.key, t.value, t.indent,
                        t.flag, out);
                case ITEM -> processItem(work, t.value, t.indent, t.flag,
                        out);
                case LITERAL -> {
                    pushIndent(out, t.indent);
                    out.append(t.text);
                }
                case POP_PATH -> path.remove(path.size() - 1);
            }
        }
    }

    /**
     * Queue an Object's pairs (insertion order) so they pop in order,
     * then — when {@code close} — the {@code }} closer literal at
     * {@code closeIndent} (for a nested pair's body that is the PAIR's
     * indent, i.e. one below the body indent; § 5.9.1). The root Object
     * passes {@code close=false}: § 5.9.3 has no root closer.
     */
    private void expandObjectBody(ArrayDeque<Task> work, Value.Obj obj,
            int indent, boolean isRoot, boolean close, int closeIndent) {
        if (close) {
            work.push(Task.literal("}\n", closeIndent));
        }
        List<Map.Entry<String, Value>> entries = new ArrayList<>(
                obj.entries().entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            work.push(Task.pair(entries.get(i).getKey(),
                    entries.get(i).getValue(), indent, isRoot && i == 0));
        }
    }

    /**
     * Queue an Array's items (insertion order) then, when {@code close},
     * the {@code ]} closer at {@code closeIndent}. {@code rootArray}
     * exposes only index 0 to root-kind detection (§ 5.9.6).
     */
    private void expandArray(ArrayDeque<Task> work, List<Value> items,
            int indent, boolean close, int closeIndent, boolean rootArray) {
        if (close) {
            work.push(Task.literal("]\n", closeIndent));
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            work.push(Task.item(items.get(i), indent, rootArray && i == 0));
        }
    }

    private void processPair(ArrayDeque<Task> work, String key, Value value,
            int indent, boolean rootFirstKey, StringBuilder out) {
        // § 5.9.0 EmptyKeyName, before the key's first byte is pushed.
        // The key is on the path when the rejection is raised — the
        // core's attach_path names the offending pair's key. For a
        // compound body the key stays on the path while the queued
        // children run (they pop it via POP_PATH), so a nested
        // rejection's snapshot names the FULL path.
        path.add(key);
        boolean leaf = true;
        try {
            if (key.isEmpty()) {
                throw new Unrep("EmptyKeyName", new ArrayList<>(path));
            }
            pushIndent(out, indent);
            pushEscapedKeySegment(key, rootFirstKey, out);
            if (mode == Mode.FORCE) {
                String text = forcedStringText(value);
                if (text != null) {
                    PureWriterText.emitStringStatic(text, indent, canonical,
                            out);
                    return;
                }
            }
            if (value instanceof Value.Null) {
                out.append(": null\n");
            } else if (value instanceof Value.Bool b) {
                out.append(": ").append(b.value() ? "true" : "false")
                        .append('\n');
            } else if (value instanceof Value.Int i) {
                out.append(": ").append(i.text()).append('\n');
            } else if (value instanceof Value.Flt f) {
                if (mode == Mode.CANONICAL) {
                    out.append(": ")
                            .append(PureWriterText.canonicalFloatChecked(
                                    f.text()))
                            .append('\n');
                } else {
                    PureWriterText.rejectNonFinite(f.text());
                    out.append(": ").append(f.text()).append('\n');
                }
            } else if (value instanceof Value.Str s) {
                PureWriterText.emitStringStatic(s.value(), indent, canonical,
                        out);
            } else if (value instanceof Value.Arr a) {
                List<Value> items = a.items();
                if (items.isEmpty()) {
                    out.append(": []\n");
                } else {
                    out.append(": [\n");
                    // Compound body: the pair's key stays on the path
                    // while the queued children run — exactly ONE
                    // POP_PATH is queued here (pushed first, so it pops
                    // AFTER the body's children and the "]" closer,
                    // restoring LIFO), and the finally below then skips
                    // its own pop ({@code leaf = false}).
                    work.push(Task.popPath());
                    expandArray(work, items, indent + 1, true, indent,
                            false);
                    leaf = false; // POP_PATH owns the pop now
                }
            } else {
                Value.Obj o = (Value.Obj) value;
                if (o.entries().isEmpty()) {
                    out.append(": {}\n");
                } else {
                    out.append(": {\n");
                    work.push(Task.popPath());
                    expandObjectBody(work, o, indent + 1, false, true,
                            indent);
                    leaf = false; // POP_PATH owns the pop now
                }
            }
        } catch (Unrep e) {
            // Re-raise keeping the DEEPEST path: a nested throw already
            // carries its own (longer) snapshot.
            throw new Unrep(e.code, e.pathSnapshot != null
                    ? e.pathSnapshot
                    : new ArrayList<>(path));
        } finally {
            if (leaf) {
                path.remove(path.size() - 1);
            }
        }
    }

    private void processItem(ArrayDeque<Task> work, Value value, int indent,
            boolean isRootArrayFirst, StringBuilder out) {
        pushIndent(out, indent);
        if (mode == Mode.FORCE) {
            String text = forcedStringText(value);
            if (text != null) {
                PureWriterText.emitItemStatic(text, indent, isRootArrayFirst,
                        canonical, out);
                return;
            }
        }
        if (value instanceof Value.Null) {
            out.append("null\n");
        } else if (value instanceof Value.Bool b) {
            out.append(b.value() ? "true" : "false").append('\n');
        } else if (value instanceof Value.Int i) {
            out.append(i.text()).append('\n');
        } else if (value instanceof Value.Flt f) {
            if (mode == Mode.CANONICAL) {
                out.append(PureWriterText.canonicalFloatChecked(f.text()))
                        .append('\n');
            } else {
                PureWriterText.rejectNonFinite(f.text());
                out.append(f.text()).append('\n');
            }
        } else if (value instanceof Value.Str s) {
            PureWriterText.emitItemStatic(s.value(), indent, isRootArrayFirst,
                    canonical, out);
        } else if (value instanceof Value.Arr a) {
            List<Value> items = a.items();
            if (items.isEmpty()) {
                out.append("[]\n");
            } else {
                out.append("[\n");
                expandArray(work, items, indent + 1, true, indent, false);
            }
        } else {
            Value.Obj o = (Value.Obj) value;
            if (o.entries().isEmpty()) {
                out.append("{}\n");
            } else {
                out.append("{\n");
                expandObjectBody(work, o, indent + 1, false, true, indent);
            }
        }
    }

    // ------------------------------------------------------------------
    // Delegators to PureWriterText (the shared rendering primitives)
    // ------------------------------------------------------------------

    /** {@code level * 4} spaces. */
    static void pushIndent(StringBuilder out, int level) {
        PureWriterText.pushIndent(out, level);
    }

    /** § 5.9.10 bare/quoted key form selection and re-escaping. */
    static void pushEscapedKeySegment(String key, boolean rootFirstKey,
            StringBuilder out) {
        PureWriterText.pushEscapedKeySegment(key, rootFirstKey, out);
    }

    /** § 5.9.8 canonical float text (checked); see PureWriterText. */
    static String canonicalFloatChecked(String text) {
        return PureWriterText.canonicalFloatChecked(text);
    }

    /** Canonical-mode String emission, shared with the formatter. */
    static void emitCanonicalStringInPair(String s, int indent,
            StringBuilder out) {
        PureWriterText.emitCanonicalStringInPair(s, indent, out);
    }

    /** Canonical-mode String item emission, shared with the formatter. */
    static void emitCanonicalStringAsItem(String s, int indent,
            boolean isRootArrayFirst, StringBuilder out) {
        PureWriterText.emitCanonicalStringAsItem(s, indent,
                isRootArrayFirst, out);
    }

    /**
     * Force-strings textual form of a leaf scalar; {@code null} for
     * compounds, which keep their structure.
     */
    private static String forcedStringText(Value v) {
        if (v instanceof Value.Null) {
            return "null";
        }
        if (v instanceof Value.Bool b) {
            return b.value() ? "true" : "false";
        }
        if (v instanceof Value.Int i) {
            return i.text();
        }
        if (v instanceof Value.Flt f) {
            return f.text();
        }
        if (v instanceof Value.Str s) {
            return s.value();
        }
        return null;
    }

    /** § 5.9.3: only the root Array's first item can force the wrap. */
    private static boolean firstItemNeedsWrap(Value item) {
        return item instanceof Value.Obj || item instanceof Value.Arr;
    }

    // ------------------------------------------------------------------
    // Envelope construction
    // ------------------------------------------------------------------

    /**
     * Build the {@code UnrepresentableAt} {@link KtavException}: the
     * envelope fields mirror the Rust core ({@code error} =
     * {@code "UnrepresentableAt"}, {@code reason} = the code,
     * {@code path} = decoded segments, § 5.9.0 vs § 5.9.7
     * {@code spec_section}), and {@code message} is the core's Display
     * rendering: the code plus {@code at [...]} when the path is
     * non-empty.
     */
    static KtavException unrepresentable(String code, List<String> path) {
        String spec = code.equals("CRByte")
                || code.equals("BothFormsRequired")
                || code.equals("TrailingWhitespaceCollision")
                || code.equals("LeadingWhitespaceCollision")
                ? "§5.9.7" : "§5.9.0";
        String message = code + pathSuffix(path);
        // The path is ALWAYS a present list (empty for ScalarRoot and
        // the document-level rejections) — the envelope contract is ten
        // present fields.
        ErrorEnvelope env = ErrorEnvelope.of("UnrepresentableAt", code,
                null, null, null, null,
                List.copyOf(path), null, null,
                spec, message);
        return KtavException.fromEnvelope(env);
    }

    /** The {@code at ["a", "b"]} suffix; omitted for an empty path. */
    private static String pathSuffix(List<String> path) {
        if (path.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" at [");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(quoteSegment(path.get(i)));
        }
        return sb.append(']').toString();
    }

    /**
     * Rust string {@code Debug} of one path segment, per Unicode scalar:
     * the usual single-character escapes plus brace escapes (backslash,
     * "u", lowercase hex) for non-printables; lone surrogates — possible
     * only in Java — escape too.
     */
    private static String quoteSegment(String s) {
        StringBuilder sb = new StringBuilder("\"");
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            switch (cp) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\0' -> sb.append("\\0");
                default -> {
                    if (isPrintable(cp)) {
                        sb.appendCodePoint(cp);
                    } else {
                        sb.append("\\u{").append(Integer.toHexString(cp))
                                .append('}');
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Conservative stand-in for Rust printability (see WriterPrecheck). */
    private static boolean isPrintable(int cp) {
        int type = Character.getType(cp);
        return type != Character.CONTROL
                && type != Character.FORMAT
                && type != Character.SURROGATE
                && type != Character.PRIVATE_USE
                && type != Character.UNASSIGNED
                && type != Character.LINE_SEPARATOR
                && type != Character.PARAGRAPH_SEPARATOR;
    }

    // ------------------------------------------------------------------
    // (Buffer pre-sizing removed: amortized StringBuilder growth, no
    // recursive pre-pass over programmatically deep trees.)
    // ------------------------------------------------------------------
}
