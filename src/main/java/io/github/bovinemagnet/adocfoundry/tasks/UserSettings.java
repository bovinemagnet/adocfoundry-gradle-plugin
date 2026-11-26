package io.github.bovinemagnet.adocfoundry.tasks;

import io.github.bovinemagnet.adocfoundry.tool.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * AdocFoundry's user settings file, which the app reads and the CLI never does. The plugin looks
 * at it only to warn that its {@code [pdf]} tables do not reach the build (spec §4.4).
 */
final class UserSettings {

    private UserSettings() {}

    /**
     * Where the app keeps its settings: {@code <config dir>/adocfoundry/adocfoundry.toml}, the
     * config directory being {@code $XDG_CONFIG_HOME} or {@code ~/.config} on Linux,
     * {@code ~/Library/Application Support} on macOS and {@code %APPDATA%} on Windows.
     *
     * @param os the operating system
     * @param environment the environment variables
     * @param userHome Java's {@code user.home}, used when {@code HOME} is not set
     * @return the file's path, if the config directory is known
     */
    static Optional<Path> file(Platform.Os os, Map<String, String> environment, String userHome) {
        String home = nonEmpty(environment.get("HOME"), userHome);
        Path configDir;
        switch (os) {
            case LINUX: {
                String xdg = environment.get("XDG_CONFIG_HOME");
                configDir = xdg != null && Path.of(xdg).isAbsolute() ? Path.of(xdg) : Path.of(home, ".config");
                break;
            }
            case MACOS:
                configDir = Path.of(home, "Library", "Application Support");
                break;
            default: {
                String appData = environment.get("APPDATA");
                if (appData == null || appData.isEmpty()) {
                    return Optional.empty();
                }
                configDir = Path.of(appData);
            }
        }
        return Optional.of(configDir.resolve("adocfoundry").resolve("adocfoundry.toml"));
    }

    /**
     * Whether the file has a line beginning {@code [pdf]} or {@code [pdf.}: a {@code [pdf]} or
     * {@code [pdf.profiles.…]} table.
     *
     * @param file the settings file
     * @return {@code false} too when the file is missing or unreadable
     */
    static boolean setsPdf(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::stripLeading)
                    .anyMatch(line -> line.startsWith("[pdf]") || line.startsWith("[pdf."));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The settings file on this machine, when it sets PDF options.
     *
     * @return the file, or empty
     */
    static Optional<Path> settingPdf() {
        return file(Platform.current().os(), System.getenv(), System.getProperty("user.home"))
                .filter(UserSettings::setsPdf);
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
