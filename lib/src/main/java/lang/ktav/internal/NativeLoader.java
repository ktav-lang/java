package lang.ktav.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;

/**
 * Resolves the on-disk path to the {@code ktav_cabi} shared library.
 * Resolution order mirrors the Go binding:
 *
 * <ol>
 *   <li>{@code $KTAV_LIB_PATH}, if set and points at an existing file.</li>
 *   <li>{@code <userCache>/ktav-java/v<VERSION>/<asset>}, if already
 *       downloaded.</li>
 *   <li>Downloaded from the matching GitHub Release asset into (2).</li>
 * </ol>
 *
 * "Version" is {@link #LIB_VERSION}, synced with the Rust crate version
 * at release time — a stale cache from an older library version triggers
 * a fresh download.
 */
final class NativeLoader {

    /** Version of {@code ktav_cabi} this build expects. Bump per release. */
    static final String LIB_VERSION = "0.1.0";

    private static final String RELEASE_BASE =
            "https://github.com/ktav-lang/java/releases/download/v";

    private NativeLoader() {
    }

    static String resolve() {
        String env = System.getenv("KTAV_LIB_PATH");
        if (env != null && !env.isEmpty()) {
            Path p = Path.of(env);
            if (!Files.exists(p)) {
                throw new IllegalStateException(
                        "KTAV_LIB_PATH=\"" + env + "\": file not found");
            }
            return p.toAbsolutePath().toString();
        }

        String asset = assetName();
        Path cacheDir = userCacheDir().resolve("ktav-java").resolve("v" + LIB_VERSION);
        Path target = cacheDir.resolve(asset);

        if (Files.exists(target)) {
            return target.toAbsolutePath().toString();
        }

        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "cannot create cache dir " + cacheDir, e);
        }

        String url = RELEASE_BASE + LIB_VERSION + "/" + asset;
        try {
            download(url, target);
        } catch (IOException e) {
            throw new IllegalStateException("fetch " + url + ": " + e, e);
        }
        return target.toAbsolutePath().toString();
    }

    private static Path userCacheDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", ".");
        if (os.startsWith("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isEmpty()) {
                return Path.of(local);
            }
            return Path.of(home, "AppData", "Local");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Caches");
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        if (xdg != null && !xdg.isEmpty()) {
            return Path.of(xdg);
        }
        return Path.of(home, ".cache");
    }

    private static String assetName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String archKey = switch (arch) {
            case "amd64", "x86_64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            default -> throw new IllegalStateException(
                    "unsupported arch: " + arch);
        };
        if (os.startsWith("win")) {
            return "ktav_cabi-windows-" + archKey + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "libktav_cabi-darwin-" + archKey + ".dylib";
        }
        if (os.contains("linux")) {
            return "libktav_cabi-linux-" + archKey + ".so";
        }
        throw new IllegalStateException("unsupported OS: " + os);
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static void download(String url, Path target) throws IOException {
        // PID-suffixed tmp file so concurrent JVMs don't fight over one path.
        Path tmp = target.resolveSibling(
                target.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<Path> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(tmp);
            throw new IOException("download interrupted: " + url, e);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }

        if (resp.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }

        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomic) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
