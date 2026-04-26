package lang.ktav;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dynamic representation of a Ktav document, mirroring the Rust crate's
 * {@code ktav::value::Value} enum. Seven variants:
 *
 * <ul>
 *   <li>{@link Null}   — the {@code null} keyword.</li>
 *   <li>{@link Bool}   — {@code true} / {@code false}.</li>
 *   <li>{@link Int}    — typed integer ({@code :i}). Held as text for
 *       arbitrary precision; convert via {@link Int#toBigInteger()} /
 *       {@link Int#toLong()}.</li>
 *   <li>{@link Flt}    — typed float ({@code :f}). Held as text for
 *       exact round-trip; convert via {@link Flt#toDouble()}.</li>
 *   <li>{@link Str}    — untyped scalar / string leaf.</li>
 *   <li>{@link Arr}    — {@code [ ... ]} array.</li>
 *   <li>{@link Obj}    — {@code { ... }} object (also the top-level
 *       document). Key order is preserved.</li>
 * </ul>
 *
 * The sealed hierarchy maps to {@code switch} expressions with pattern
 * matching (JDK 21+) or {@code instanceof} checks. Variants are named
 * with short suffixes ({@code Int}, {@code Flt}, {@code Str}, {@code Arr},
 * {@code Obj}) to avoid clashing with {@link java.lang} classes at use
 * sites.
 */
public sealed interface Value
        permits Value.Null, Value.Bool, Value.Int, Value.Flt,
                Value.Str, Value.Arr, Value.Obj {

    /** The {@code null} keyword — a single shared instance. */
    enum Null implements Value {
        NULL;
    }

    /** The {@code true} / {@code false} keywords. */
    record Bool(boolean value) implements Value {
        public static final Bool TRUE = new Bool(true);
        public static final Bool FALSE = new Bool(false);

        public static Bool of(boolean v) {
            return v ? TRUE : FALSE;
        }
    }

    /**
     * Typed integer scalar (the {@code :i} form). Payload is the digit
     * form with an optional leading minus; leading plus is stripped at
     * parse time. Arbitrary precision — use {@link #toBigInteger()} when
     * the value may exceed {@code long}.
     */
    record Int(String text) implements Value {
        public Int {
            Objects.requireNonNull(text, "text");
        }

        public BigInteger toBigInteger() {
            return new BigInteger(text);
        }

        public long toLong() {
            return Long.parseLong(text);
        }

        public static Int of(long v) {
            return new Int(Long.toString(v));
        }

        public static Int of(BigInteger v) {
            return new Int(v.toString());
        }
    }

    /**
     * Typed float scalar (the {@code :f} form). Payload is the textual
     * mantissa with a decimal point and optional scientific exponent —
     * held as text so precision round-trips exactly.
     */
    record Flt(String text) implements Value {
        public Flt {
            Objects.requireNonNull(text, "text");
        }

        public double toDouble() {
            return Double.parseDouble(text);
        }

        public static Flt of(double v) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalArgumentException(
                        "Ktav floats must be finite: " + v);
            }
            String s = Double.toString(v);
            if (s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) {
                s = s + ".0";
            }
            return new Flt(s);
        }
    }

    /** Untyped scalar / string leaf. */
    record Str(String value) implements Value {
        public Str {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Multi-line {@code [ ... ]} array. The compact constructor copies
     * the input into an unmodifiable list so the value is genuinely
     * immutable — callers can pass any {@link List} without worrying
     * about aliasing, and {@link #items()} cannot be mutated through.
     */
    record Arr(List<Value> items) implements Value {
        public Arr {
            Objects.requireNonNull(items, "items");
            items = List.copyOf(items);
        }
    }

    /**
     * Multi-line {@code { ... }} object. Key order is preserved on both
     * the parse and render paths — this is the top-level document shape.
     *
     * <p>The compact constructor copies the input into an unmodifiable
     * map so the value is genuinely immutable. Iterating the original
     * input map's order is preserved.
     */
    record Obj(Map<String, Value> entries) implements Value {
        public Obj {
            Objects.requireNonNull(entries, "entries");
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }
}
