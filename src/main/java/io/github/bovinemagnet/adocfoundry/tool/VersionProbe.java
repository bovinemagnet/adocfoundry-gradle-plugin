package io.github.bovinemagnet.adocfoundry.tool;

import java.nio.file.Path;

/** Runs {@code <executable> --version}. */
@FunctionalInterface
public interface VersionProbe {

    /**
     * Runs the binary's {@code --version}.
     *
     * @param executable the binary
     * @return its standard output
     */
    String versionOutput(Path executable);
}
