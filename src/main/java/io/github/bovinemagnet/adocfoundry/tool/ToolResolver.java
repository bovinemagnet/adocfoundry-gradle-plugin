package io.github.bovinemagnet.adocfoundry.tool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Finds the {@code adocfoundry-cli} to run: the build's own {@code executable}, else the copy
 * cached in the Gradle user home, else a verified download.
 *
 * <p>The cache is {@code <gradleUserHome>/caches/adocfoundry/<version>/<platform>/}. A download
 * is checked against the release's {@code SHA256SUMS}, extracted beside the cache folder and
 * moved into place in one step, all under a lock on {@code <version>/.lock}, so concurrent builds
 * download once and never see a half-written cache.
 */
public final class ToolResolver {

    private static final Logger LOGGER = Logging.getLogger(ToolResolver.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(15);
    private static final String MARKER = ".sha256";

    /** A file lock is held per JVM, so threads of one JVM take turns before locking the file. */
    private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private final Path cacheRoot;
    private final boolean offline;
    private final ArchiveExtractor extractor;
    private final VersionProbe probe;

    /**
     * @param gradleUserHome the Gradle user home, which holds the cache
     * @param offline whether the build runs with {@code --offline}
     * @param extractor extracts a verified archive
     * @param probe runs a binary's {@code --version}
     */
    public ToolResolver(Path gradleUserHome, boolean offline, ArchiveExtractor extractor, VersionProbe probe) {
        this.cacheRoot = gradleUserHome.resolve("caches").resolve("adocfoundry");
        this.offline = offline;
        this.extractor = extractor;
        this.probe = probe;
    }

    /**
     * Resolves the request to a binary whose version has been checked.
     *
     * @param request what the build asks for
     * @return the binary to run
     */
    public Path resolve(ToolRequest request) {
        Path binary;
        if (request.executable() != null) {
            binary = checkExecutable(request.executable());
        } else {
            ToolVersion requested = ToolVersion.parse(request.version());
            requireMinimum(requested, "adocfoundry.toolVersion is " + request.version());
            binary = cachedOrDownloaded(request);
        }
        checkVersion(binary, request);
        return binary;
    }

    private static Path checkExecutable(Path executable) {
        if (!Files.exists(executable)) {
            throw new GradleException("adocfoundry.executable " + executable + " does not exist. Point it at"
                    + " adocfoundry-cli, or remove it to download the CLI.");
        }
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new GradleException("adocfoundry.executable " + executable + " is not an executable file."
                    + " Point it at adocfoundry-cli, or remove it to download the CLI.");
        }
        return executable;
    }

    private void checkVersion(Path binary, ToolRequest request) {
        ToolVersion actual = ToolVersion.fromVersionOutput(probe.versionOutput(binary));
        requireMinimum(actual, binary + " is adocfoundry-cli " + actual);
        if (request.executable() != null && !actual.toString().equals(ToolVersion.parse(request.version()).toString())) {
            LOGGER.warn("adocfoundry.executable {} is adocfoundry-cli {}, not adocfoundry.toolVersion {}.",
                    binary, actual, request.version());
        }
    }

    private static void requireMinimum(ToolVersion version, String what) {
        if (version.compareTo(ToolVersion.MINIMUM) < 0) {
            throw new GradleException(what + ", but this plugin needs adocfoundry-cli " + ToolVersion.MINIMUM
                    + " or later. Set adocfoundry.toolVersion to " + ToolVersion.MINIMUM
                    + " or later, or point adocfoundry.executable at a newer binary.");
        }
    }

    private Path cachedOrDownloaded(ToolRequest request) {
        Platform platform = request.platform();
        Path versionDir = cacheRoot.resolve(request.version());
        Path platformDir = versionDir.resolve(platform.id());
        Path binary = platformDir.resolve(platform.executableName());
        if (isComplete(platformDir, binary)) {
            return binary;
        }
        if (offline) {
            throw new GradleException("adocfoundry-cli " + request.version() + " for " + platform
                    + " is not in the Gradle user home cache, and the build runs with --offline. Run the build"
                    + " once without --offline to download it, or set adocfoundry.executable to a local binary.");
        }
        try {
            Files.createDirectories(versionDir);
            withLock(versionDir.resolve(".lock"), () -> {
                if (!isComplete(platformDir, binary)) {
                    download(request, versionDir, platformDir);
                }
            });
        } catch (IOException e) {
            throw new GradleException("Could not prepare the adocfoundry-cli cache in " + versionDir + ": "
                    + e.getMessage(), e);
        }
        return binary;
    }

    private static boolean isComplete(Path platformDir, Path binary) {
        return Files.isRegularFile(binary) && Files.isRegularFile(platformDir.resolve(MARKER));
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private static void withLock(Path lockFile, IoAction action) throws IOException {
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockFile.toAbsolutePath().normalize(), p -> new ReentrantLock());
        jvmLock.lock();
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = channel.lock();
            try {
                action.run();
            } finally {
                lock.release();
            }
        } finally {
            jvmLock.unlock();
        }
    }

    private void download(ToolRequest request, Path versionDir, Path platformDir) throws IOException {
        Platform platform = request.platform();
        String asset = ReleaseAssets.assetName(request.version(), platform);
        boolean zip = ReleaseAssets.isZip(platform);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        URI sumsUri = ReleaseAssets.sumsUri(request.baseUrl(), request.version());
        Path sumsFile = versionDir.resolve(".download-" + UUID.randomUUID() + "-SHA256SUMS");
        Sha256Sums sums;
        try {
            fetch(client, sumsUri, sumsFile);
            sums = Sha256Sums.parse(Files.readString(sumsFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(sumsFile);
        }
        sums.requireEntry(asset);

        LOGGER.lifecycle("Downloading adocfoundry-cli {} for {}", request.version(), platform);
        URI assetUri = ReleaseAssets.assetUri(request.baseUrl(), request.version(), platform);
        Path archive = versionDir.resolve(".download-" + UUID.randomUUID() + "-" + asset);
        Path staging = versionDir.resolve(".staging-" + platform.id() + "-" + UUID.randomUUID());
        try {
            String actual = fetch(client, assetUri, archive);
            sums.requireMatch(asset, actual);
            ArchiveEntries.requireSafe(archive, zip, asset);
            extractor.extract(archive, zip, staging, platform.executableName());
            Path stagedBinary = staging.resolve(platform.executableName());
            if (!Files.isRegularFile(stagedBinary)) {
                throw new GradleException(asset + " has no " + platform.executableName() + " at its top level."
                        + " Set adocfoundry.executable to a local binary.");
            }
            if (!stagedBinary.toFile().setExecutable(true, false) && !Files.isExecutable(stagedBinary)) {
                throw new GradleException("Could not make " + stagedBinary + " executable.");
            }
            Files.writeString(staging.resolve(MARKER), actual, StandardCharsets.UTF_8);
            deleteTree(platformDir);
            Files.move(staging, platformDir, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(archive);
            deleteTree(staging);
        }
    }

    /** Downloads {@code uri} to {@code target} and returns its SHA-256. */
    private static String fetch(HttpClient client, URI uri, Path target) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(DOWNLOAD_TIMEOUT).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw downloadFailure(uri, e.toString(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("The download of " + uri + " was interrupted.", e);
        }
        MessageDigest digest = sha256();
        Instant deadline = Instant.now().plus(DOWNLOAD_TIMEOUT);
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw downloadFailure(uri, "HTTP " + response.statusCode(), null);
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (Instant.now().isAfter(deadline)) {
                        throw downloadFailure(uri, "it took longer than " + DOWNLOAD_TIMEOUT.toMinutes() + " minutes", null);
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
        } catch (IOException e) {
            throw downloadFailure(uri, e.toString(), e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static GradleException downloadFailure(URI uri, String reason, Throwable cause) {
        return new GradleException("Could not download " + uri + ": " + reason + ". Check adocfoundry.toolVersion"
                + " and adocfoundry.baseUrl, or set adocfoundry.executable to a local binary.", cause);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
