package lang.ktav;

/**
 * Thrown when the native library rejects an input — parse failure for
 * {@link Ktav#loads}, render failure for {@link Ktav#dumps}. The message
 * is the UTF-8 string returned by the native side.
 */
public final class KtavException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public KtavException(String message) {
        super(message);
    }

    public KtavException(String message, Throwable cause) {
        super(message, cause);
    }
}
