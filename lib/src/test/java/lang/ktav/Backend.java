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

    /** Byte-level entry. Only the pure backend has one (UTF-8 before grammar). */
    Value loads(byte[] src);

    /** Whether {@link #loads(byte[])} exists (raw_bytes corpus fixtures). */
    boolean hasByteEntry();

    /** Whether the backend needs the built cabi (guards + CI job split). */
    boolean needsNativeLibrary();

    Value loads(String src);

    Value loadsStrict(String src);

    String dumps(Value value);

    String toStringForceStrings(Value value);

    String emitCanonical(Value value);

    String canonicalFromSource(String src);

    String format(String src);

    static Backend nativeBackend() {
        return new Backend() {
            @Override public String name() {
                return "native";
            }

            @Override public Value loads(byte[] src) {
                throw new UnsupportedOperationException(
                        "the native Java API takes a String; the byte -> String"
                                + " boundary is the InvalidUtf8 rejection point");
            }

            @Override public boolean hasByteEntry() {
                return false;
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

            @Override public boolean hasByteEntry() {
                return true;
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
        };
    }
}
