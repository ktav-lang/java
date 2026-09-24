package lang.ktav;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import lang.ktav.internal.ErrorEnvelope;
import lang.ktav.internal.NativeLib;
import lang.ktav.internal.WireJson;

import java.nio.charset.StandardCharsets;

/**
 * Public facade for the Ktav configuration format. Thin wrapper around
 * the native {@code ktav_cabi} library — see {@link Value} for the data
 * model.
 *
 * <pre>{@code
 * Value doc = Ktav.loads("port: 8080\nname: app\n");
 * String text = Ktav.dumps(doc);
 * }</pre>
 *
 * <p>The native library is loaded lazily on first call. Override the
 * lookup path with {@code KTAV_LIB_PATH} (useful for local dev or air-
 * gapped environments). Otherwise the matching binary is downloaded once
 * from the companion GitHub Release into the user cache.
 */
public final class Ktav {

    private Ktav() {
    }

    /**
     * Parse a Ktav document into a {@link Value}. Throws
     * {@link KtavException} on any parse error.
     */
    public static Value loads(String src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        byte[] input = src.getBytes(StandardCharsets.UTF_8);
        byte[] output = callNative(NativeOp.LOADS, input);
        return WireJson.decode(output);
    }

    /**
     * Parse a Ktav document with strict numeric spelling checks.
     *
     * @param src Ktav source text
     * @return decoded value tree
     * @throws KtavException when strict parsing rejects the source
     */
    public static Value loadsStrict(String src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        byte[] input = src.getBytes(StandardCharsets.UTF_8);
        byte[] output = callNative(NativeOp.LOADS_STRICT, input);
        return WireJson.decode(output);
    }

    /**
     * Render a {@link Value} back to Ktav text. The top-level value must
     * be a {@link Value.Obj} or {@link Value.Arr}. Top-level arrays are
     * supported as of spec 0.1.1 (binding 0.3.1). Throws
     * {@link KtavException} on render error.
     *
     * <p>Unrepresentable values (spec &sect; 5.9.0) are rejected with the
     * spec's reason codes surfaced through
     * {@link KtavException#getReason()} / {@link KtavException#getPath()}:
     * {@code ScalarRoot} for a non-Obj/non-Arr root, and
     * {@code NonFiniteFloat} for a {@link Value.Flt} whose text parses to
     * NaN or &plusmn;Infinity. Both conditions are invisible to the native
     * writers (the JSON wire rejects them first as opaque {@code
     * Message}-class errors), so this binding checks for them itself —
     * see {@code WriterPrecheck}.
     */
    public static String dumps(Value value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        WriterPrecheck.checkRepresentable(value);
        byte[] input = WireJson.encode(value);
        byte[] output = callNative(NativeOp.DUMPS, input);
        return new String(output, StandardCharsets.UTF_8);
    }

    /**
     * Render a {@link Value} back to Ktav text with every leaf scalar
     * coerced to a String. Integers, floats, booleans, and {@code null}
     * are flattened to their textual form via the raw-marker ({@code ::}).
     * Compounds (objects and arrays) preserve their structure; only leaf
     * scalars are coerced. The output round-trips back through
     * {@link #loads} as the same set of String scalars.
     *
     * <p>Useful for "everything is a string" dumps — e.g. for downstream
     * consumers that don't understand typed scalars, or for diff-friendly
     * canonical text.
     *
     * <p>The top-level value must be a {@link Value.Obj} or
     * {@link Value.Arr}. Throws {@link KtavException} on render error.
     *
     * <p>Unlike {@link #dumps} and {@link #emitCanonical}, this does NOT
     * reject non-finite floats: they are coerced to their textual String
     * form before the write, and the output is exactly what the core's
     * force-strings writer produces (e.g. {@code f: NaN}); the JSON wire
     * cannot carry such payloads at all, so the binding performs the leaf
     * coercion itself. {@link #dumps} and {@link #emitCanonical} reject
     * them with the {@code NonFiniteFloat} reason instead. The
     * {@code ScalarRoot} root check still applies — see
     * {@code WriterPrecheck}.
     *
     * @since 0.3.1
     */
    public static String toStringForceStrings(Value value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        value = WriterPrecheck.forCoercingWriter(value);
        byte[] input = WireJson.encode(value);
        byte[] output = callNative(NativeOp.DUMPS_FORCE_STRINGS, input);
        return new String(output, StandardCharsets.UTF_8);
    }

    /**
     * Render a {@link Value} as the deterministic canonical Ktav form
     * (spec § 7). The output is stable across runs and can be used for
     * hashing, diffing, or storage. The top-level value must be a
     * {@link Value.Obj} or {@link Value.Arr}. Throws {@link KtavException}
     * on render error.
     *
     * <p>Unrepresentable values (spec &sect; 5.9.0) are rejected with the
     * spec's reason codes surfaced through
     * {@link KtavException#getReason()} / {@link KtavException#getPath()}:
     * {@code ScalarRoot} for a non-Obj/non-Arr root, and
     * {@code NonFiniteFloat} for a {@link Value.Flt} whose text parses to
     * NaN or &plusmn;Infinity. Both conditions are invisible to the native
     * writers (the JSON wire rejects them first as opaque {@code
     * Message}-class errors), so this binding checks for them itself —
     * see {@code WriterPrecheck}.
     *
     * @since 0.5.0
     */
    public static String emitCanonical(Value value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        WriterPrecheck.checkRepresentable(value);
        byte[] input = WireJson.encode(value);
        byte[] output = callNative(NativeOp.EMIT_CANONICAL, input);
        return new String(output, StandardCharsets.UTF_8);
    }

    /**
     * Parse Ktav source text and immediately re-emit it in canonical
     * form (spec &sect; 5.9), preserving the source's insertion order of
     * object keys. Equivalent to {@code emitCanonical(loads(src))}, but
     * in one native call instead of two: {@link Value.Int} and
     * {@link Value.Flt} already store their text form verbatim
     * (arbitrary precision, exact round-trip), so this binding has no
     * float-fidelity gap either way — the win here is skipping the
     * intermediate {@link Value} tree and its JSON wire encode/decode.
     * Comments and blank lines do NOT survive — canonical form carries
     * no trivia; use {@link #format} for that.
     *
     * @param src Ktav source text
     * @return canonical Ktav source text
     * @throws KtavException when the native side rejects the source
     * @since 0.8.0
     */
    public static String canonicalFromSource(String src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        byte[] input = src.getBytes(StandardCharsets.UTF_8);
        byte[] output = callNative(NativeOp.CANONICAL_FROM_SOURCE, input);
        return new String(output, StandardCharsets.UTF_8);
    }

    /**
     * Comment-preserving formatter. The input is Ktav SOURCE TEXT (like
     * {@link #loads}), not a {@link Value} — unlike the render methods
     * this round-trips through the document's own trivia.
     *
     * <p>Guarantees:
     * <ul>
     *   <li>Every comment is preserved verbatim (spec &sect; 3.4: a
     *       comment owns a whole line).</li>
     *   <li>Blank lines survive as grouping hints, but runs of 2+ blank
     *       lines collapse to one, and blank padding immediately inside
     *       brackets is dropped — hence the formatter is a fixed point:
     *       {@code format(format(x)).equals(format(x))}.</li>
     *   <li>Key order is never changed.</li>
     *   <li>For documents with no comments and no blank lines the result
     *       equals {@code emitCanonical(loads(src))}.</li>
     * </ul>
     *
     * @param src Ktav source text
     * @return formatted Ktav source text
     * @throws KtavException when the native side rejects the source
     */
    public static String format(String src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        byte[] input = src.getBytes(StandardCharsets.UTF_8);
        byte[] output = callNative(NativeOp.FORMAT, input);
        return new String(output, StandardCharsets.UTF_8);
    }

    /**
     * Version of the loaded {@code ktav_cabi} native library. Useful for
     * sanity checks.
     */
    public static String nativeVersion() {
        String v = NativeLib.get().ktav_version();
        return v == null ? "" : v;
    }

    private enum NativeOp {
        LOADS,
        LOADS_STRICT,
        DUMPS,
        DUMPS_FORCE_STRINGS,
        EMIT_CANONICAL,
        FORMAT,
        CANONICAL_FROM_SOURCE
    }

    private static byte[] callNative(NativeOp op, byte[] input) {
        NativeLib lib = NativeLib.get();

        // try-with-resources releases the native buffer even if the JNA
        // call throws — otherwise the Memory leaks until the next GC
        // finalizer pass.
        try (Memory srcMem = input.length == 0 ? null : new Memory(input.length)) {
            Pointer srcPtr;
            if (srcMem == null) {
                srcPtr = Pointer.NULL;
            } else {
                srcMem.write(0, input, 0, input.length);
                srcPtr = srcMem;
            }

            PointerByReference outBuf = new PointerByReference();
            LongByReference outLen = new LongByReference();
            PointerByReference outErr = new PointerByReference();
            LongByReference outErrLen = new LongByReference();

            int rc = switch (op) {
                case LOADS -> lib.ktav_loads(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
                case LOADS_STRICT -> lib.ktav_loads_strict(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
                case DUMPS -> lib.ktav_dumps(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
                case DUMPS_FORCE_STRINGS -> lib.ktav_dumps_force_strings(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
                case EMIT_CANONICAL -> lib.ktav_emit_canonical(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
                case FORMAT -> lib.ktav_format(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
                case CANONICAL_FROM_SOURCE -> lib.ktav_canonical_from_source(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
            };

            if (rc != 0) {
                byte[] payload = copyAndFreeBytes(lib, outErr.getValue(), outErrLen.getValue());
                if (payload.length == 0) {
                    throw new KtavException("native call failed with code " + rc);
                }
                throw KtavException.fromEnvelope(ErrorEnvelope.parse(payload));
            }

            return copyAndFreeBytes(lib, outBuf.getValue(), outLen.getValue());
        }
    }

    private static byte[] copyAndFreeBytes(NativeLib lib, Pointer ptr, long len) {
        if (ptr == null || len <= 0) {
            return new byte[0];
        }
        byte[] out = ptr.getByteArray(0, toIntLen(len));
        lib.ktav_free(ptr, len);
        return out;
    }

    private static int toIntLen(long len) {
        if (len > Integer.MAX_VALUE) {
            throw new KtavException("native buffer too large: " + len);
        }
        return (int) len;
    }
}
