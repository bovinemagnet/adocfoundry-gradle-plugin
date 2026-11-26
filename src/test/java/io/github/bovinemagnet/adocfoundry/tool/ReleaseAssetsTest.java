package io.github.bovinemagnet.adocfoundry.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ReleaseAssetsTest {

    private static final Platform LINUX = Platform.of("Linux", "amd64");

    @Test
    void buildsReleaseUrls() {
        String base = "https://github.com/bovinemagnet/AdocFoundry/releases/download";

        assertThat(ReleaseAssets.assetUri(base, "0.15.0", LINUX)).isEqualTo(URI.create(
                base + "/v0.15.0/adocfoundry-cli-0.15.0-linux-x86_64.tar.gz"));
        assertThat(ReleaseAssets.sumsUri(base, "0.15.0")).isEqualTo(URI.create(base + "/v0.15.0/SHA256SUMS"));
    }

    @Test
    void toleratesATrailingSlash() {
        assertThat(ReleaseAssets.sumsUri("https://mirror.example/releases/", "0.15.0"))
                .isEqualTo(URI.create("https://mirror.example/releases/v0.15.0/SHA256SUMS"));
    }

    @Test
    void zipsOnlyOnWindows() {
        assertThat(ReleaseAssets.isZip(Platform.of("Windows 11", "amd64"))).isTrue();
        assertThat(ReleaseAssets.isZip(LINUX)).isFalse();
    }
}
