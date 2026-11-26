package io.github.bovinemagnet.adocfoundry.tool;

import java.nio.file.Path;

/** Extracts the CLI and its licences from a release archive. */
@FunctionalInterface
public interface ArchiveExtractor {

    /**
     * Extracts {@code executableName} and {@code licenses/} from the archive's top level into
     * {@code destination}, leaving everything else out.
     *
     * @param archive the archive, already verified
     * @param zip whether it is a zip rather than a gzipped tar
     * @param destination an empty or missing folder
     * @param executableName the binary's name in the archive
     */
    void extract(Path archive, boolean zip, Path destination, String executableName);
}
