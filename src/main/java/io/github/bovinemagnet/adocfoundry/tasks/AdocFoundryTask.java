package io.github.bovinemagnet.adocfoundry.tasks;

import io.github.bovinemagnet.adocfoundry.tool.AdocFoundryToolService;
import io.github.bovinemagnet.adocfoundry.tool.Platform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

/** A task that runs {@code adocfoundry-cli} over the workspace. */
public abstract class AdocFoundryTask extends DefaultTask {

    /** The group every AdocFoundry task is listed in. */
    public static final String GROUP = "documentation";

    /**
     * @return the service that finds the CLI
     */
    @ServiceReference(AdocFoundryToolService.NAME)
    public abstract Property<AdocFoundryToolService> getToolService();

    /**
     * @return {@code adocfoundry.toolVersion}
     */
    @Input
    public abstract Property<String> getToolVersion();

    /**
     * @return {@code adocfoundry.baseUrl}, which does not change what the CLI produces
     */
    @Internal
    public abstract Property<String> getBaseUrl();

    /**
     * @return {@code adocfoundry.executable}, when set
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExecutable();

    /**
     * @return the folder opened in AdocFoundry, passed to the CLI as the root
     */
    @Internal
    public abstract DirectoryProperty getWorkspaceDir();

    /**
     * @return the workspace's files, less what AdocFoundry leaves out ({@link DocsTreeExclusions})
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getDocsTree();

    /**
     * @return the project directory, the CLI's working directory
     */
    @Internal
    public abstract DirectoryProperty getProjectDir();

    /**
     * @return Gradle's process service
     */
    @Inject
    protected abstract ExecOperations getExecOperations();

    /**
     * The binary this task runs.
     *
     * @return the resolved CLI
     */
    protected Path executable() {
        File executable = getExecutable().isPresent() ? getExecutable().get().getAsFile() : null;
        return getToolService().get().resolve(getToolVersion().get(), getBaseUrl().get(), executable);
    }

    /**
     * Who ran what, for failure messages.
     *
     * @param command the CLI command
     * @param executable the binary
     * @return the invocation
     */
    protected Invocation invocation(String command, Path executable) {
        return new Invocation(getPath(), command, executable, Platform.current());
    }

    /**
     * Runs the CLI in the project directory and captures what it prints.
     *
     * @param executable the binary
     * @param arguments the arguments, with absolute paths
     * @return what it gave back
     */
    protected CliResult run(Path executable, List<String> arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ExecResult result;
        try {
            result = getExecOperations().exec(spec -> {
                spec.setExecutable(executable.toString());
                spec.args(arguments);
                spec.setWorkingDir(getProjectDir().get().getAsFile());
                spec.setIgnoreExitValue(true);
                spec.setStandardOutput(out);
                spec.setErrorOutput(err);
            });
        } catch (RuntimeException e) {
            throw AdocFoundryToolService.cannotStart(executable, e);
        }
        return new CliResult(result.getExitValue(), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /**
     * Logs each line of the CLI's standard error at warn level.
     *
     * @param result what the CLI gave back
     */
    protected void logWarnings(CliResult result) {
        result.stderr().lines().filter(line -> !line.isBlank()).forEach(line -> getLogger().warn(line));
    }
}
