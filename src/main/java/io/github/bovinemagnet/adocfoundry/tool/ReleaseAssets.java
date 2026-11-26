package io.github.bovinemagnet.adocfoundry.tool;

import java.net.URI;

/**
 * Where a release's files are. This is the one place that maps a platform to its asset, so a
 * change to AdocFoundry's release layout touches nothing else.
 */
public final class ReleaseAssets {

    private ReleaseAssets() {}

    /**
     * The asset holding {@code adocfoundry-cli} for a platform.
     *
     * <p>Windows has no standalone CLI archive yet (AdocFoundry#806), so the CLI comes from the
     * application's zip.
     *
     * @param version the release version, such as {@code 0.15.0}
     * @param platform the platform
     * @return the asset's file name
     */
    public static String assetName(String version, Platform platform) {
        if (platform.os() == Platform.Os.WINDOWS) {
            return "AdocFoundry-" + version + "-" + platform.id() + ".zip";
        }
        return "adocfoundry-cli-" + version + "-" + platform.id() + ".tar.gz";
    }

    /**
     * Whether the platform's asset is a zip rather than a gzipped tar.
     *
     * @param platform the platform
     * @return {@code true} on Windows
     */
    public static boolean isZip(Platform platform) {
        return platform.os() == Platform.Os.WINDOWS;
    }

    /**
     * The URL of the platform's asset.
     *
     * @param baseUrl the releases' base URL
     * @param version the release version
     * @param platform the platform
     * @return {@code <baseUrl>/v<version>/<asset>}
     */
    public static URI assetUri(String baseUrl, String version, Platform platform) {
        return releaseFile(baseUrl, version, assetName(version, platform));
    }

    /**
     * The URL of the release's checksums.
     *
     * @param baseUrl the releases' base URL
     * @param version the release version
     * @return {@code <baseUrl>/v<version>/SHA256SUMS}
     */
    public static URI sumsUri(String baseUrl, String version) {
        return releaseFile(baseUrl, version, "SHA256SUMS");
    }

    private static URI releaseFile(String baseUrl, String version, String file) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + "/v" + version + "/" + file);
    }
}
