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

    int ktav_dumps(
            Pointer src,
            long srcLen,
            PointerByReference outBuf,
            LongByReference outLen,
            PointerByReference outErr,
            LongByReference outErrLen);

    void ktav_free(Pointer ptr, long len);

    String ktav_version();

    final class Holder {
        private static final NativeLib INSTANCE = loadSingleton();

        private static NativeLib loadSingleton() {
            String path = NativeLoader.resolve();
            return Native.load(path, NativeLib.class);
        }

        private Holder() {
        }
    }

    static NativeLib get() {
        return Holder.INSTANCE;
    }
}
