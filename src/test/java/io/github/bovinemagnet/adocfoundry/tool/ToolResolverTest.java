package io.github.bovinemagnet.adocfoundry.tool;

import static io.github.bovinemagnet.adocfoundry.tool.FakeRelease.entries;
import static io.github.bovinemagnet.adocfoundry.tool.FakeRelease.sha256;
import static io.github.bovinemagnet.adocfoundry.tool.FakeRelease.tarGz;
import static io.github.bovinemagnet.adocfoundry.tool.FakeRelease.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolResolverTest {

    private static final String VERSION = "0.15.0";
    private static final Platform LINUX = Platform.of("Linux", "amd64");
    private static final Platform WINDOWS = Platform.of("Windows 11", "amd64");
    private static final String LINUX_ASSET = "adocfoundry-cli-0.15.0-linux-x86_64.tar.gz";
    private static final String WINDOWS_ASSET = "AdocFoundry-0.15.0-windows-x64.zip";
    private static final VersionProbe CURRENT = executable -> "adocfoundry-cli 0.15.0\n";

    @TempDir
    Path temp;

    private Path gradleUserHome;
    private FakeRelease release;
    private ArchiveExtractor extractor;

    /** Gradle's own archive and copy services, as a build service is given them. */
    public abstract static class Services {
        @Inject
        public abstract ArchiveOperations getArchiveOperations();

        @Inject
        public abstract FileSystemOperations getFileSystemOperations();
    }

    @BeforeEach
    void setUp() {
        gradleUserHome = temp.resolve("gradle-user-home");
        release = new FakeRelease();
        Project project = ProjectBuilder.builder().withProjectDir(temp.resolve("project").toFile()).build();
        Services services = project.getObjects().newInstance(Services.class);
        extractor = new GradleArchiveExtractor(services.getArchiveOperations(), services.getFileSystemOperations());
    }

    @AfterEach
    void tearDown() {
        release.close();
    }

    private ToolResolver resolver(boolean offline, VersionProbe probe) {
        return new ToolResolver(gradleUserHome, offline, extractor, probe);
    }

    private ToolRequest request(Platform platform) {
        return new ToolRequest(VERSION, release.baseUrl(), null, platform);
    }

    private void publishLinux(byte[] archive) {
        release.serve("v0.15.0/" + LINUX_ASSET, archive)
                .serve("v0.15.0/SHA256SUMS", (sha256(archive) + "  " + LINUX_ASSET + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] linuxArchive() {
        return tarGz(entries(
                "adocfoundry-cli", "#!/bin/sh\necho adocfoundry-cli 0.15.0\n",
                "licenses/SCOWL.txt", "scowl",
                "licenses/THIRD-PARTY-NOTICES.txt", "notices"));
    }

    private Path cachedBinary(Platform platform) {
        return gradleUserHome.resolve("caches/adocfoundry/0.15.0/" + platform.id() + "/" + platform.executableName());
    }

    @Test
    void downloadsVerifiesAndCaches() throws IOException {
        publishLinux(linuxArchive());

        Path binary = resolver(false, CURRENT).resolve(request(LINUX));

        assertThat(binary).isEqualTo(cachedBinary(LINUX));
        assertThat(Files.readString(binary)).contains("adocfoundry-cli 0.15.0");
        assertThat(binary.getParent().resolve("licenses/SCOWL.txt")).exists();
        assertThat(binary.getParent().resolve(".sha256")).hasContent(sha256(linuxArchive()));
        if (File.separatorChar == '/') {
            assertThat(Files.isExecutable(binary)).isTrue();
        }
        assertThat(release.requestsFor("v0.15.0/SHA256SUMS")).isEqualTo(1);
        assertThat(release.requestsFor("v0.15.0/" + LINUX_ASSET)).isEqualTo(1);
        try (Stream<Path> left = Files.list(binary.getParent().getParent())) {
            assertThat(left.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder(".lock", "linux-x86_64");
        }

        int before = release.totalRequests();
        Path again = resolver(false, CURRENT).resolve(request(LINUX));

        assertThat(again).isEqualTo(binary);
        assertThat(release.totalRequests()).isEqualTo(before);
    }

    @Test
    void refusesWrongHash() {
        byte[] archive = linuxArchive();
        String wrong = sha256("something else".getBytes(StandardCharsets.UTF_8));
        release.serve("v0.15.0/" + LINUX_ASSET, archive)
                .serve("v0.15.0/SHA256SUMS", (wrong + "  " + LINUX_ASSET + "\n").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> resolver(false, CURRENT).resolve(request(LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining(wrong)
                .hasMessageContaining(sha256(archive));
        assertThat(cachedBinary(LINUX)).doesNotExist();
        assertThat(cachedBinary(LINUX).getParent().getParent()).isDirectory();
        try (Stream<Path> left = Files.list(cachedBinary(LINUX).getParent().getParent())) {
            assertThat(left.map(p -> p.getFileName().toString())).containsExactly(".lock");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void refusesMissingEntry() {
        release.serve("v0.15.0/" + LINUX_ASSET, linuxArchive())
                .serve("v0.15.0/SHA256SUMS", (sha256(new byte[0]) + "  other.tar.gz\n").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> resolver(false, CURRENT).resolve(request(LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("SHA256SUMS has no line for " + LINUX_ASSET);
        assertThat(release.requestsFor("v0.15.0/" + LINUX_ASSET)).isZero();
    }

    @Test
    void followsRedirects() {
        byte[] archive = linuxArchive();
        release.redirect("v0.15.0/" + LINUX_ASSET, "objects/asset")
                .serve("objects/asset", archive)
                .serve("v0.15.0/SHA256SUMS", (sha256(archive) + "  " + LINUX_ASSET + "\n").getBytes(StandardCharsets.UTF_8));

        assertThat(resolver(false, CURRENT).resolve(request(LINUX))).isEqualTo(cachedBinary(LINUX));
        assertThat(release.requestsFor("objects/asset")).isEqualTo(1);
    }

    @Test
    void failsOnAMissingAsset() {
        release.serve("v0.15.0/SHA256SUMS", (sha256(new byte[0]) + "  " + LINUX_ASSET + "\n").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> resolver(false, CURRENT).resolve(request(LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("404")
                .hasMessageContaining(LINUX_ASSET);
    }

    @Test
    void concurrentResolutionDownloadsOnce() throws Exception {
        publishLinux(linuxArchive());
        release.delay(300);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Path>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                // A resolver each, as separate builds and daemons would have.
                calls.add(() -> resolver(false, CURRENT).resolve(request(LINUX)));
            }
            for (Future<Path> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(cachedBinary(LINUX));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(release.requestsFor("v0.15.0/" + LINUX_ASSET)).isEqualTo(1);
    }

    @Test
    void refusesEntryOutsideTheArchive() {
        publishLinux(tarGz(entries(
                "adocfoundry-cli", "binary",
                "../escaped.txt", "outside")));

        assertThatThrownBy(() -> resolver(false, CURRENT).resolve(request(LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("../escaped.txt");
        assertThat(cachedBinary(LINUX)).doesNotExist();
        try (Stream<Path> files = Files.walk(gradleUserHome)) {
            assertThat(files.filter(p -> p.getFileName().toString().equals("escaped.txt"))).isEmpty();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void refusesAbsoluteEntry() {
        publishLinux(tarGz(entries(
                "adocfoundry-cli", "binary",
                "/tmp/absolute.txt", "outside")));

        assertThatThrownBy(() -> resolver(false, CURRENT).resolve(request(LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("/tmp/absolute.txt");
        assertThat(cachedBinary(LINUX)).doesNotExist();
    }

    @Test
    void extractsOnlyTheCliFromTheWindowsZip() {
        byte[] archive = zip(entries(
                "licenses/SCOWL.txt", "scowl",
                "adocfoundry.exe", "app",
                "adocfoundry-cli.exe", "cli",
                "adoc-ls.exe", "ls",
                "README.md", "readme"));
        release.serve("v0.15.0/" + WINDOWS_ASSET, archive)
                .serve("v0.15.0/SHA256SUMS", (sha256(archive) + "  " + WINDOWS_ASSET + "\n").getBytes(StandardCharsets.UTF_8));

        Path binary = resolver(false, CURRENT).resolve(request(WINDOWS));

        assertThat(binary).isEqualTo(cachedBinary(WINDOWS)).hasContent("cli");
        try (Stream<Path> files = Files.walk(binary.getParent())) {
            assertThat(files.filter(Files::isRegularFile).map(p -> binary.getParent().relativize(p).toString().replace('\\', '/')))
                    .containsExactlyInAnyOrder("adocfoundry-cli.exe", "licenses/SCOWL.txt", ".sha256");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void failsWhenTheArchiveHasNoCli() {
        publishLinux(tarGz(entries("licenses/SCOWL.txt", "scowl")));

        assertThatThrownBy(() -> resolver(false, CURRENT).resolve(request(LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("has no adocfoundry-cli");
    }

    @Test
    void usesExecutableWithoutDownloading() throws IOException {
        Path executable = Files.writeString(temp.resolve("my-cli"), "binary");
        executable.toFile().setExecutable(true);

        Path resolved = resolver(false, CURRENT).resolve(new ToolRequest(VERSION, release.baseUrl(), executable, LINUX));

        assertThat(resolved).isEqualTo(executable);
        assertThat(release.totalRequests()).isZero();
        assertThat(gradleUserHome.resolve("caches/adocfoundry")).doesNotExist();
    }

    @Test
    void failsWhenTheExecutableIsMissing() {
        Path executable = temp.resolve("missing-cli");

        assertThatThrownBy(() -> resolver(false, CURRENT).resolve(new ToolRequest(VERSION, release.baseUrl(), executable, LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining(executable.toString())
                .hasMessageContaining("does not exist");
    }

    @Test
    void offlineWithoutCacheFails() {
        publishLinux(linuxArchive());

        assertThatThrownBy(() -> resolver(true, CURRENT).resolve(request(LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("--offline")
                .hasMessageContaining("adocfoundry.executable")
                .hasMessageContaining("0.15.0");
        assertThat(release.totalRequests()).isZero();
    }

    @Test
    void offlineUsesTheCache() {
        publishLinux(linuxArchive());
        resolver(false, CURRENT).resolve(request(LINUX));
        int before = release.totalRequests();

        assertThat(resolver(true, CURRENT).resolve(request(LINUX))).isEqualTo(cachedBinary(LINUX));
        assertThat(release.totalRequests()).isEqualTo(before);
    }

    @Test
    void refusesOldToolVersion() {
        publishLinux(linuxArchive());

        assertThatThrownBy(() -> resolver(false, executable -> "adocfoundry-cli 0.14.2").resolve(request(LINUX)))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("0.14.2")
                .hasMessageContaining("0.15.0");
    }

    @Test
    void acceptsADifferentVersionOfTheGivenExecutable() throws IOException {
        Path executable = Files.writeString(temp.resolve("my-cli"), "binary");
        executable.toFile().setExecutable(true);

        Path resolved = resolver(false, e -> "adocfoundry-cli 0.16.0")
                .resolve(new ToolRequest(VERSION, release.baseUrl(), executable, LINUX));

        assertThat(resolved).isEqualTo(executable);
    }
}
