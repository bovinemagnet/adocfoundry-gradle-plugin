package io.github.bovinemagnet.adocfoundry.tool;

import java.nio.file.Path;

/**
 * Which {@code adocfoundry-cli} a build asks for.
 *
 * @param version the version to download, or to expect of {@code executable}
 * @param baseUrl where releases are downloaded from
 * @param executable a local binary to use in place of a download, or {@code null}
 * @param platform the platform to download for
 */
public record ToolRequest(String version, String baseUrl, Path executable, Platform platform) {}
