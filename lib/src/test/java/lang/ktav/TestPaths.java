package lang.ktav;

import lang.ktav.internal.NativeLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Hardcoded paths for the test suite. We implement one specific spec
 * version and the cabi build lives in the same workspace, so there's
 * nothing to configure.
 *
 * <p>Gradle runs tests from {@code lib/} (the subproject); both the
 * cabi build dir and the spec submodule live one level up at the repo
 * root.
 */
final class TestPaths {

    private static final Path REPO = Paths.get("..").toAbsolutePath().normalize();

    static final Path CABI = cabiPath();
    static final Path SPEC = REPO.resolve("spec").resolve("versions").resolve("0.6").resolve("tests");

    static {
        String override = System.getenv("KTAV_LIB_PATH");
        if (override != null && !override.isEmpty()) {
            NativeLoader.setLibraryPath(override);
        } else if (Files.isRegularFile(CABI)) {
            NativeLoader.setLibraryPath(CABI.toString());
        }
    }

    /** Forces class loading so the static block runs. */
    static void init() {
    }

    static boolean cabiBuilt() {
        return Files.isRegularFile(CABI);
    }

    static boolean specPresent() {
        return Files.isDirectory(SPEC);
    }

    private static String cabiName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "ktav_cabi.dll";
        if (os.contains("mac")) return "libktav_cabi.dylib";
        return "libktav_cabi.so";
    }

    private static Path cabiPath() {
        String target = System.getenv("CARGO_TARGET_DIR");
        Path base = target == null || target.isEmpty()
                ? REPO.resolve("target")
                : Paths.get(target);
        return base.resolve("release").resolve(cabiName());
    }

    private TestPaths() {
    }
}
