package io.github.bovinemagnet.adocfoundry.tasks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Exports one {@code pdfs} entry with {@code adocfoundry-cli pdf}. The look is the workspace's:
 * {@code --theme}, {@code --fonts-dir} and {@code --standard} are passed only when the entry sets
 * them (spec §4.4).
 */
public abstract class PdfTask extends AdocFoundryTask {

    /**
     * Which of {@code component}, {@code page} and {@code profile} an entry sets.
     *
     * @param component the component version, or {@code null}
     * @param page the page, or {@code null}
     * @param profile the profile, or {@code null}
     */
    public record Selector(String component, File page, String profile) {

        /**
         * What is wrong with the selection.
         *
         * @return a description such as {@code sets none}, or {@code null} when exactly one is set
         */
        public String problem() {
            List<String> set = new ArrayList<>();
            if (component != null) {
                set.add("component");
            }
            if (page != null) {
                set.add("page");
            }
            if (profile != null) {
                set.add("profile");
            }
            if (set.size() == 1) {
                return null;
            }
            if (set.isEmpty()) {
                return "sets none";
            }
            String last = set.remove(set.size() - 1);
            return "sets " + String.join(", ", set) + " and " + last;
        }
    }

    /**
     * @return the entry's name, for messages
     */
    @Internal
    public abstract Property<String> getEntryName();

    /**
     * @return {@code [<version>@]<name>} of a component version to export as a book
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getComponent();

    /**
     * @return a page to export as an article
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @org.gradle.api.tasks.Optional
    public abstract RegularFileProperty getPage();

    /**
     * @return a {@code [pdf.profiles.<name>]} of the workspace's {@code .adocfoundry.toml}
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getProfile();

    /**
     * @return a theme name or {@code .yml} file passed as {@code --theme}; normally unset
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getTheme();

    /**
     * @return the theme file, when {@code theme} names one, as an input wherever it lies
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getThemeFile();

    /**
     * @return a fonts directory passed as {@code --fonts-dir}; normally unset
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @org.gradle.api.tasks.Optional
    public abstract DirectoryProperty getFontsDir();

    /**
     * @return standards passed as {@code --standard}, such as {@code pdf-a-2a}
     */
    @Input
    public abstract ListProperty<String> getStandards();

    /**
     * @return {@code none}, {@code warning} or {@code error}
     */
    @Input
    public abstract Property<String> getFailOn();

    /**
     * @return where the PDF is written
     */
    @OutputFile
    public abstract RegularFileProperty getOutput();

    /**
     * @return whether to warn that AdocFoundry's user settings set PDF options
     */
    @Internal
    public abstract Property<Boolean> getWarnAboutUserSettings();

    /**
     * The CLI arguments.
     *
     * @param workspace the workspace root
     * @param selector what to export, exactly one set
     * @param theme {@code --theme}, already absolute when it is a file, or {@code null}
     * @param fontsDir {@code --fonts-dir}, or {@code null}
     * @param standards the {@code --standard} values
     * @param failOn the threshold
     * @param output the PDF
     * @return the arguments
     */
    static List<String> arguments(File workspace, Selector selector, String theme, File fontsDir,
            List<String> standards, String failOn, File output) {
        String root = workspace.getAbsolutePath();
        List<String> arguments = new ArrayList<>();
        arguments.add("pdf");
        if (selector.component() != null) {
            arguments.addAll(List.of(root, "--root", root, "--component", selector.component()));
        } else if (selector.page() != null) {
            arguments.addAll(List.of(selector.page().getAbsolutePath(), "--root", root));
        } else {
            arguments.addAll(List.of("--profile", selector.profile(), root));
        }
        arguments.addAll(List.of("--output", output.getAbsolutePath()));
        if (theme != null) {
            arguments.addAll(List.of("--theme", theme));
        }
        if (fontsDir != null) {
            arguments.addAll(List.of("--fonts-dir", fontsDir.getAbsolutePath()));
        }
        for (String standard : standards) {
            arguments.addAll(List.of("--standard", standard));
        }
        arguments.addAll(List.of("--fail-on", failOn));
        return arguments;
    }

    /**
     * {@code --theme}'s value: a name as given, a {@code .yml} file made absolute against the
     * project directory.
     *
     * @param theme the entry's theme
     * @param projectDir the project directory
     * @return the value to pass
     */
    public static String themeArgument(String theme, File projectDir) {
        if (!isThemeFile(theme)) {
            return theme;
        }
        File file = new File(theme);
        return file.isAbsolute() ? file.getPath() : new File(projectDir, theme).getPath();
    }

    /**
     * Whether a theme value names a file, which the CLI decides by its {@code .yml} ending.
     *
     * @param theme the entry's theme
     * @return whether it is a file
     */
    public static boolean isThemeFile(String theme) {
        return theme.toLowerCase(Locale.ROOT).endsWith(".yml");
    }

    /** Exports the PDF. */
    @TaskAction
    public void export() {
        Selector selector = new Selector(
                getComponent().getOrNull(),
                getPage().isPresent() ? getPage().get().getAsFile() : null,
                getProfile().getOrNull());
        String problem = selector.problem();
        if (problem != null) {
            throw new GradleException("adocfoundry.pdfs." + getEntryName().get()
                    + " must set exactly one of component, page and profile, but it " + problem + ".");
        }
        if (getWarnAboutUserSettings().get()) {
            UserSettings.settingPdf().ifPresent(file -> getLogger().warn("AdocFoundry's user settings file {} sets PDF"
                    + " options. The build does not read it, so this PDF can differ from one the app exports on"
                    + " this machine. Put the look in the repository, in .adocfoundry.toml, antora.yml or the"
                    + " playbook. Set adocfoundry.warnAboutUserSettings = false to silence this.", file));
        }

        File output = getOutput().get().getAsFile();
        try {
            Files.deleteIfExists(output.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String theme = getTheme().isPresent()
                ? themeArgument(getTheme().get(), getProjectDir().get().getAsFile())
                : null;
        File fontsDir = getFontsDir().isPresent() ? getFontsDir().get().getAsFile() : null;
        Path executable = executable();
        CliResult result = run(executable, arguments(getWorkspaceDir().get().getAsFile(), selector, theme, fontsDir,
                getStandards().get(), getFailOn().get(), output));

        result.stdout().lines().filter(line -> !line.isBlank()).forEach(line -> getLogger().lifecycle(line));
        logWarnings(result);
        Optional<GradleException> failure = ExitCodes.failure(invocation("pdf", executable), result, List.of(), null,
                "Fix the problems, or set adocfoundry.pdfs." + getEntryName().get() + ".failOn to a higher threshold.");
        if (failure.isPresent()) {
            throw failure.get();
        }
    }
}
