package lang.ktav.internal;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * JNA binding to {@code ktav_cabi}. All targets are 64-bit, so {@code
 * size_t} is 8 bytes → Java {@code long} matches without a custom
 * {@code IntegerType}.
 *
 * <p>The library is loaded lazily via {@link #get()}, which resolves the
 * native file through {@link NativeLoader}.
 */
public interface NativeLib extends Library {

    int ktav_loads(
            Pointer src,
            long srcLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    int ktav_loads_strict(
            Pointer src,
            long srcLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    int ktav_dumps(
            Pointer src,
            long srcLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    int ktav_dumps_force_strings(
            Pointer src,
            long srcLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    int ktav_emit_canonical(
            Pointer src,
            long srcLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    int ktav_canonical_from_source(
            Pointer src,
            long srcLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    int ktav_format(
            Pointer src,
            long srcLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    int ktav_edit_value(
            Pointer src,
            long srcLen,
            Pointer path,
            long pathLen,
            Pointer replacement,
            long replacementLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    int ktav_edit_value_segments(
            Pointer src,
            long srcLen,
            Pointer path,
            long pathLen,
            Pointer replacement,
            long replacementLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    void ktav_free(Pointer ptr, long len);

    String ktav_version();

    /** ABI version reported by the loaded native library. */
    int ktav_abi_version();

    /**
     * The ABI version this binding is built against. 0.9 raises it from
     * 1 to 2 (the exported set grew by the source-edit symbols); an
     * ABI-1 native binary is REJECTED with a clear version-mismatch
     * error — never silently used, never hidden behind a pure fallback.
     */
    int EXPECTED_ABI_VERSION = 2;

    final class Holder {
        private static final NativeLib INSTANCE = loadSingleton();

        private static NativeLib loadSingleton() {
            String path = NativeLoader.resolve();
            NativeLib lib = Native.load(path, NativeLib.class);
            int abi = lib.ktav_abi_version();
            if (abi != EXPECTED_ABI_VERSION) {
                throw new IllegalStateException(
                        "ktav_cabi ABI version mismatch: loaded library "
                                + "reports ABI " + abi + " but this binding"
                                + " requires ABI " + EXPECTED_ABI_VERSION
                                + " (source-edit entry points need the 0.9"
                                + " native library): " + path);
            }
            return lib;
        }

        private Holder() {
        }
    }

    static NativeLib get() {
        return Holder.INSTANCE;
    }
}
