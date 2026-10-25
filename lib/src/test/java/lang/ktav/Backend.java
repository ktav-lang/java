package lang.ktav;

/**
 * One parameterized harness over BOTH backends: the native
 * {@code ktav_cabi} facade ({@link Ktav}) and the pure-Java
 * {@link lang.ktav.pure.PureKtav}. {@link CorpusHarness} runs every
 * corpus category through this interface, so neither implementation is
 * tested against a private copy of the runner.
 *
 * <p>Package-private test seam — production code never sees it.
 */
interface Backend {

    String name();

    /** Byte-level entry, preserving the original source bytes. */
    Value loads(byte[] src);

    /** Whether the backend needs the built cabi (guards + CI job split). */
    boolean needsNativeLibrary();

    Value loads(String src);

    Value loadsStrict(String src);

    String dumps(Value value);

    String toStringForceStrings(Value value);

    String emitCanonical(Value value);

    String canonicalFromSource(String src);

    String format(String src);

    /** Lossless source edit by textual path (0.9). */
    String editValue(String src, String path, Value replacement);

    /** Lossless source edit by decoded segments (0.9). */
    String editValueSegments(String src, java.util.List<String> segments,
            Value replacement);

    static Backend nativeBackend() {
        return new Backend() {
            @Override public String name() {
                return "native";
            }

            @Override public Value loads(byte[] src) {
                return Ktav.loads(src);
            }

            @Override public boolean needsNativeLibrary() {
                return true;
            }

            @Override public Value loads(String src) {
                return Ktav.loads(src);
            }

            @Override public Value loadsStrict(String src) {
                return Ktav.loadsStrict(src);
            }

            @Override public String dumps(Value value) {
                return Ktav.dumps(value);
            }

            @Override public String toStringForceStrings(Value value) {
                return Ktav.toStringForceStrings(value);
            }

            @Override public String emitCanonical(Value value) {
                return Ktav.emitCanonical(value);
            }

            @Override public String canonicalFromSource(String src) {
                return Ktav.canonicalFromSource(src);
            }

            @Override public String format(String src) {
                return Ktav.format(src);
            }

            @Override public String editValue(String src, String path,
                    Value replacement) {
                return Ktav.editValue(src, path, replacement);
            }

            @Override public String editValueSegments(String src,
                    java.util.List<String> segments, Value replacement) {
                return Ktav.editValueSegments(src, segments, replacement);
            }
        };
    }

    static Backend pureBackend() {
        return new Backend() {
            @Override public String name() {
                return "pure";
            }

            @Override public Value loads(byte[] src) {
                return lang.ktav.pure.PureKtav.loads(src);
            }

            @Override public boolean needsNativeLibrary() {
                return false;
            }

            @Override public Value loads(String src) {
                return lang.ktav.pure.PureKtav.loads(src);
            }

            @Override public Value loadsStrict(String src) {
                return lang.ktav.pure.PureKtav.loadsStrict(src);
            }

            @Override public String dumps(Value value) {
                return lang.ktav.pure.PureKtav.dumps(value);
            }

            @Override public String toStringForceStrings(Value value) {
                return lang.ktav.pure.PureKtav.toStringForceStrings(value);
            }

            @Override public String emitCanonical(Value value) {
                return lang.ktav.pure.PureKtav.emitCanonical(value);
            }

            @Override public String canonicalFromSource(String src) {
                return lang.ktav.pure.PureKtav.canonicalFromSource(src);
            }

            @Override public String format(String src) {
                return lang.ktav.pure.PureKtav.format(src);
            }

            @Override public String editValue(String src, String path,
                    Value replacement) {
                return lang.ktav.pure.PureKtav.editValue(src, path,
                        replacement);
            }

            @Override public String editValueSegments(String src,
                    java.util.List<String> segments, Value replacement) {
                return lang.ktav.pure.PureKtav.editValueSegments(src,
                        segments, replacement);
            }
        };
    }
}
