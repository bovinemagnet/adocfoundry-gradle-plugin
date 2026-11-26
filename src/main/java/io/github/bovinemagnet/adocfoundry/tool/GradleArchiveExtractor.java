package io.github.bovinemagnet.adocfoundry.tool;

import java.nio.file.Path;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;

/** Extracts with Gradle's own archive support, so the plugin needs no archive library. */
public final class GradleArchiveExtractor implements ArchiveExtractor {

    private final ArchiveOperations archives;
    private final FileSystemOperations files;

    /**
     * @param archives Gradle's archive service
     * @param files Gradle's file service
     */
    public GradleArchiveExtractor(ArchiveOperations archives, FileSystemOperations files) {
        this.archives = archives;
        this.files = files;
    }

    @Override
    public void extract(Path archive, boolean zip, Path destination, String executableName) {
        FileTree tree = zip
                ? archives.zipTree(archive.toFile())
                : archives.tarTree(archives.gzip(archive.toFile()));
        files.copy(spec -> {
            spec.from(tree);
            spec.into(destination.toFile());
            spec.include(executableName, "licenses/**");
        });
    }
}
