package io.github.bovinemagnet.adocfoundry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

/**
 * A throwaway Gradle project for a functional test, run by TestKit with the plugin under test on
 * its classpath.
 *
 * <p>Every build runs with {@code --configuration-cache}, in a TestKit home that is never the
 * developer's own Gradle user home, and with the user config directory that AdocFoundry's settings
 * live in pointed at an empty folder, so the developer's settings never reach a test.
 */
final class TestProject {

    private final Path projectDir;
    private final Path userConfigDir;
    private final String gradleVersion;
    private final Map<String, String> extraEnvironment = new HashMap<>();
    private boolean pluginClasspath = true;

    /**
     * A project in a new folder beneath {@code root}, which the caller owns (a JUnit temporary
     * directory).
     */
    TestProject(Path root, String gradleVersion) {
        this.projectDir = root.resolve("project");
        this.userConfigDir = root.resolve("user-config");
        this.gradleVersion = gradleVersion;
        try {
            Files.createDirectories(projectDir);
            Files.createDirectories(userConfigDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The Gradle versions every functional test runs on, from the build. */
    static Stream<String> gradleVersions() {
        return Arrays.stream(System.getProperty("adocfoundry.gradleVersions").split(","))
                .map(String::trim)
                .filter(version -> !version.isEmpty());
    }

    Path dir() {
        return projectDir;
    }

    /** The folder a test sees as the platform's user config directory. */
    Path userConfigDir() {
        return userConfigDir;
    }

    /**
     * Writes {@code settings.gradle} and a {@code build.gradle} applying the plugin, then
     * {@code body}. When {@code ADOCFOUNDRY_CLI} is set, the build runs that binary instead of
     * downloading one.
     */
    TestProject buildFile(String body) {
        String cli = System.getenv("ADOCFOUNDRY_CLI");
        String executable = cli == null || cli.isEmpty()
                ? ""
                : "adocfoundry.executable = file('" + groovyString(cli) + "')\n\n";
        write("settings.gradle", "rootProject.name = 'fixture'\n");
        write("build.gradle", "plugins {\n    id 'io.github.bovinemagnet.adocfoundry'\n}\n\n" + executable + body);
        return this;
    }

    /** Copies a folder of {@code fixtures/} into the project directory. */
    TestProject fixture(String name) {
        Path source = resource("fixtures/" + name);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path target = projectDir.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** A path or other text quoted for a single-quoted Groovy string. */
    static String groovyString(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }

    TestProject write(String relativePath, String content) {
        Path file = projectDir.resolve(relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Resolves the plugin as a real build would, from its repositories, not TestKit's classpath. */
    TestProject withoutPluginClasspath() {
        pluginClasspath = false;
        return this;
    }

    TestProject environment(String name, String value) {
        extraEnvironment.put(name, value);
        return this;
    }

    GradleRunner runner(String... arguments) {
        List<String> args = new ArrayList<>(Arrays.asList(arguments));
        args.add("--configuration-cache");
        args.add("--warning-mode=fail");
        args.add("--stacktrace");
        GradleRunner runner = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withTestKitDir(new File(System.getProperty("adocfoundry.testKitDir")))
                .withProjectDir(projectDir.toFile())
                .withEnvironment(environment())
                .withArguments(args)
                .forwardOutput();
        return pluginClasspath ? runner.withPluginClasspath() : runner;
    }

    BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    BuildResult runAndFail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    /**
     * Runs the build twice, as every functional test does: the second run must reuse the
     * configuration cache the first stored.
     */
    List<BuildResult> runTwice(String... arguments) {
        BuildResult first = run(arguments);
        BuildResult second = run(arguments);
        assertThat(second.getOutput()).contains("Reusing configuration cache.");
        return List.of(first, second);
    }

    private Map<String, String> environment() {
        Map<String, String> environment = new HashMap<>(System.getenv());
        // Where AdocFoundry looks for its user settings on each platform.
        environment.put("XDG_CONFIG_HOME", userConfigDir.toString());
        environment.put("APPDATA", userConfigDir.toString());
        environment.put("HOME", userConfigDir.resolve("home").toString());
        environment.putAll(extraEnvironment);
        return environment;
    }

    /** The absolute path of a file or folder on the functional test classpath. */
    static Path resource(String name) {
        URL url = TestProject.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new IllegalArgumentException("No resource " + name);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
