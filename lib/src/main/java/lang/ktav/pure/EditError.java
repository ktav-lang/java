package lang.ktav.pure;

/**
 * Everything that can go wrong with a lossless pure-Java source edit —
 * the behavioural twin of the Rust core's {@code ktav::edit::EditError}.
 *
 * <ul>
 *   <li>{@code InvalidPath} — the textual path is malformed; {@code
 *       offset} is the UTF-8 byte offset within the path argument where
 *       the problem was detected.</li>
 *   <li>{@code NotFound} — the path does not address an existing value
 *       (missing key, bad/out-of-bounds array index, descent through a
 *       scalar, empty path).</li>
 *   <li>{@code Unsupported} — no lossless single-range patch exists for
 *       this (path, replacement) pair.</li>
 * </ul>
 */
final class EditError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    enum Code {
        INVALID_PATH,
        NOT_FOUND,
        UNSUPPORTED
    }

    final Code code;
    /** UTF-8 byte offset into the path argument (InvalidPath only). */
    final int offset;
    /** Human-readable limitation description (Unsupported only). */
    final String reason;

    private EditError(Code code, int offset, String reason, String message) {
        super(message);
        this.code = code;
        this.offset = offset;
        this.reason = reason;
    }

    static EditError invalidPath(int offset) {
        return new EditError(Code.INVALID_PATH, offset, null,
                "invalid edit path (bad escape or empty segment) at byte "
                        + offset);
    }

    static EditError notFound() {
        return new EditError(Code.NOT_FOUND, -1, null,
                "edit path does not address an existing value");
    }

    static EditError unsupported(String reason) {
        return new EditError(Code.UNSUPPORTED, -1, reason,
                "no lossless source edit: " + reason);
    }
}
