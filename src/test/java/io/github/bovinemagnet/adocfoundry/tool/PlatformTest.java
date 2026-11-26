package io.github.bovinemagnet.adocfoundry.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlatformTest {

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
        "Linux,         amd64,   linux-x86_64,    adocfoundry-cli-0.15.0-linux-x86_64.tar.gz,    adocfoundry-cli",
        "Linux,         x86_64,  linux-x86_64,    adocfoundry-cli-0.15.0-linux-x86_64.tar.gz,    adocfoundry-cli",
        "Linux,         aarch64, linux-aarch64,   adocfoundry-cli-0.15.0-linux-aarch64.tar.gz,   adocfoundry-cli",
        "Linux,         arm64,   linux-aarch64,   adocfoundry-cli-0.15.0-linux-aarch64.tar.gz,   adocfoundry-cli",
        "Mac OS X,      x86_64,  macos-universal, adocfoundry-cli-0.15.0-macos-universal.tar.gz, adocfoundry-cli",
        "Mac OS X,      aarch64, macos-universal, adocfoundry-cli-0.15.0-macos-universal.tar.gz, adocfoundry-cli",
        "Windows 11,    amd64,   windows-x64,     AdocFoundry-0.15.0-windows-x64.zip,            adocfoundry-cli.exe",
        "Windows 10,    x86_64,  windows-x64,     AdocFoundry-0.15.0-windows-x64.zip,            adocfoundry-cli.exe",
        "Windows 11,    aarch64, windows-arm64,   AdocFoundry-0.15.0-windows-arm64.zip,          adocfoundry-cli.exe",
        "Windows 11,    arm64,   windows-arm64,   AdocFoundry-0.15.0-windows-arm64.zip,          adocfoundry-cli.exe",
    })
    void mapsEveryPlatform(String osName, String osArch, String id, String asset, String executable) {
        Platform platform = Platform.of(osName, osArch);

        assertThat(platform.id()).isEqualTo(id);
        assertThat(platform.executableName()).isEqualTo(executable);
        assertThat(ReleaseAssets.assetName("0.15.0", platform)).isEqualTo(asset);
    }

    @Test
    void refusesUnknownPlatforms() {
        assertThatThrownBy(() -> Platform.of("Linux", "riscv64"))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("Linux riscv64")
                .hasMessageContaining("executable");
        assertThatThrownBy(() -> Platform.of("FreeBSD", "amd64"))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("FreeBSD amd64");
        assertThatThrownBy(() -> Platform.of("Mac OS X", "ppc"))
                .isInstanceOf(GradleException.class);
    }

    @Test
    void onlyLinuxNeedsTheGlibcHint() {
        assertThat(Platform.of("Linux", "amd64").cannotStartHint()).contains("glibc 2.35");
        assertThat(Platform.of("Windows 11", "amd64").cannotStartHint()).isEmpty();
    }
}
