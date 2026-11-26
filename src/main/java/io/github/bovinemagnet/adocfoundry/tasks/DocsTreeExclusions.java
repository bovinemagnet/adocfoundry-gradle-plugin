package io.github.bovinemagnet.adocfoundry.tasks;

import java.io.File;
import java.util.Set;
import java.util.function.Predicate;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;

/**
 * Leaves out of the docs tree what AdocFoundry leaves out of a workspace by default
 * ({@code DEFAULT_IGNORED_DIRECTORIES} in its {@code adoc-core}): those folders at any depth,
 * except that {@code build}, {@code target} and {@code dist} beneath the {@code modules} folder of
 * an Antora component are ordinary content.
 *
 * <p>This also leaves out the CLI's diagram cache, {@code .cache}, which the CLI writes inside the
 * tree. A workspace's own {@code [workspace] ignore} list is not read.
 */
public final class DocsTreeExclusions implements Spec<FileTreeElement> {

    static final Set<String> IGNORED = Set.of(
            ".cache", ".git", ".zed", "node_modules", "build", "target", "dist", ".idea", ".gradle");
    static final Set<String> BUILD_OUTPUT = Set.of("build", "target", "dist");

    @Override
    public boolean isSatisfiedBy(FileTreeElement element) {
        String[] segments = element.getRelativePath().getSegments();
        int folders = element.isDirectory() ? segments.length : segments.length - 1;
        File root = element.getFile();
        for (int i = 0; i < segments.length; i++) {
            root = root.getParentFile();
        }
        File workspace = root;
        return isIgnored(segments, folders, folder -> new File(workspace, folder + "/antora.yml").isFile());
    }

    /**
     * Mirrors AdocFoundry's {@code is_ignored_beneath}.
     *
     * @param segments the path's segments, relative to the workspace root
     * @param folders how many leading segments are folders
     * @param isComponent whether a folder, given relative to the root with {@code /}, holds
     *     {@code antora.yml}
     * @return whether the path lies in a folder the workspace leaves out
     */
    static boolean isIgnored(String[] segments, int folders, Predicate<String> isComponent) {
        StringBuilder folder = new StringBuilder();
        int modules = -1;
        for (int position = 0; position < folders; position++) {
            String name = segments[position];
            boolean insideModules = modules >= 0 && position > modules;
            if (IGNORED.contains(name) && !(insideModules && BUILD_OUTPUT.contains(name))) {
                return true;
            }
            if (modules < 0 && name.equals("modules") && isComponent.test(folder.toString())) {
                modules = position;
            }
            if (folder.length() > 0) {
                folder.append('/');
            }
            folder.append(name);
        }
        return false;
    }
}
