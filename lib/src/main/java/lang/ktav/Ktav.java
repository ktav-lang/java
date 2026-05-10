package lang.ktav;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import lang.ktav.internal.NativeLib;
import lang.ktav.internal.WireJson;

import java.nio.charset.StandardCharsets;

/**
 * Public facade for the Ktav configuration format. Thin wrapper around
 * the native {@code ktav_cabi} library — see {@link Value} for the data
 * model.
 *
 * <pre>{@code
 * Value doc = Ktav.loads("port :i= 8080\nname = app\n");
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
     * Render a {@link Value} back to Ktav text. The top-level value must
     * be a {@link Value.Obj} or {@link Value.Arr} — other shapes are
     * rejected by the native side. Top-level arrays are supported as of
     * spec 0.1.1 (binding 0.3.1). Throws {@link KtavException} on render
     * error.
     */
    public static String dumps(Value value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        byte[] input = WireJson.encode(value);
        byte[] output = callNative(NativeOp.DUMPS, input);
        return new String(output, StandardCharsets.UTF_8);
    }

    /**
     * Render a {@link Value} back to Ktav text with every leaf scalar
     * coerced to a String. Typed integers ({@code :i}), typed floats
     * ({@code :f}), booleans, and {@code null} are flattened to their
     * textual form via the raw-marker ({@code ::}). Compounds (objects
     * and arrays) preserve their structure; only leaf scalars are
     * coerced. The output round-trips back through {@link #loads} as the
     * same set of String scalars.
     *
     * <p>Useful for "everything is a string" dumps — e.g. for downstream
     * consumers that don't understand typed markers, or for diff-friendly
     * canonical text.
     *
     * <p>The top-level value must be a {@link Value.Obj} or
     * {@link Value.Arr}. Throws {@link KtavException} on render error.
     *
     * @since 0.3.1
     */
    public static String toStringForceStrings(Value value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        byte[] input = WireJson.encode(value);
        byte[] output = callNative(NativeOp.DUMPS_FORCE_STRINGS, input);
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
        DUMPS,
        DUMPS_FORCE_STRINGS
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
                case DUMPS -> lib.ktav_dumps(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
                case DUMPS_FORCE_STRINGS -> lib.ktav_dumps_force_strings(srcPtr, input.length,
                        outBuf, outLen, outErr, outErrLen);
            };

            if (rc != 0) {
                String msg = copyAndFree(lib, outErr.getValue(), outErrLen.getValue());
                if (msg.isEmpty()) {
                    msg = "native call failed with code " + rc;
                }
                throw new KtavException(msg);
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

    private static String copyAndFree(NativeLib lib, Pointer ptr, long len) {
        byte[] b = copyAndFreeBytes(lib, ptr, len);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static int toIntLen(long len) {
        if (len > Integer.MAX_VALUE) {
            throw new KtavException("native buffer too large: " + len);
        }
        return (int) len;
    }
}
