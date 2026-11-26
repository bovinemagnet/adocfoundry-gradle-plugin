package io.github.bovinemagnet.adocfoundry.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bovinemagnet.adocfoundry.tool.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserSettingsTest {

    @TempDir
    Path temp;

    @Test
    void findsTheFileWhereAdocFoundryLooks() {
        String xdg = temp.resolve("xdg").toString();
        String home = temp.resolve("home").toString();
        String javaHome = temp.resolve("user.home").toString();

        assertThat(UserSettings.file(Platform.Os.LINUX, Map.of("XDG_CONFIG_HOME", xdg), javaHome))
                .contains(Path.of(xdg, "adocfoundry", "adocfoundry.toml"));
        assertThat(UserSettings.file(Platform.Os.LINUX, Map.of("XDG_CONFIG_HOME", "relative"), javaHome))
                .contains(Path.of(javaHome, ".config", "adocfoundry", "adocfoundry.toml"));
        assertThat(UserSettings.file(Platform.Os.LINUX, Map.of("HOME", home), javaHome))
                .contains(Path.of(home, ".config", "adocfoundry", "adocfoundry.toml"));
        assertThat(UserSettings.file(Platform.Os.MACOS, Map.of("HOME", home), javaHome))
                .contains(Path.of(home, "Library", "Application Support", "adocfoundry", "adocfoundry.toml"));
        assertThat(UserSettings.file(Platform.Os.WINDOWS, Map.of("APPDATA", xdg), javaHome))
                .contains(Path.of(xdg, "adocfoundry", "adocfoundry.toml"));
        assertThat(UserSettings.file(Platform.Os.WINDOWS, Map.of(), javaHome)).isEmpty();
    }

    @Test
    void spotsPdfTables() throws IOException {
        assertThat(UserSettings.setsPdf(write("[pdf]\ntheme = \"acme\"\n"))).isTrue();
        assertThat(UserSettings.setsPdf(write("[ui]\nx = 1\n  [pdf.profiles.guide]\npage = \"a.adoc\"\n"))).isTrue();
        assertThat(UserSettings.setsPdf(write("[ui]\n# [pdf]\nnote = \"[pdf] in text\"\n[pdfx]\n"))).isFalse();
        assertThat(UserSettings.setsPdf(temp.resolve("missing.toml"))).isFalse();
    }

    private Path write(String content) throws IOException {
        return Files.writeString(Files.createTempFile(temp, "settings", ".toml"), content);
    }
}
