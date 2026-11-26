package io.github.bovinemagnet.adocfoundry.tasks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Runs {@code adocfoundry-cli check} over the workspace and fails on diagnostics at or above
 * {@code failOn}. The JSON report is written even when the check fails.
 */
public abstract class CheckTask extends AdocFoundryTask {

    /**
     * @return {@code warning} or {@code error}
     */
    @Input
    public abstract Property<String> getFailOn();

    /**
     * @return where the CLI's JSON report is saved
     */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    /**
     * The CLI arguments.
     *
     * @param workspace the workspace root
     * @param failOn the threshold
     * @return the arguments
     */
    static List<String> arguments(File workspace, String failOn) {
        return List.of("check", workspace.getAbsolutePath(), "--format", "json", "--fail-on", failOn);
    }

    /** Runs the check. */
    @TaskAction
    public void check() {
        Path executable = executable();
        CliResult result = run(executable, arguments(getWorkspaceDir().get().getAsFile(), getFailOn().get()));
        File report = getReportFile().get().getAsFile();
        try {
            Files.createDirectories(report.toPath().getParent());
            Files.writeString(report.toPath(), result.stdout(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<Diagnostic> diagnostics = List.of();
        if (result.exitCode() == 0 || result.exitCode() == 1) {
            try {
                diagnostics = CheckReport.parse(result.stdout());
            } catch (GradleException e) {
                getLogger().warn("{}: {}", getPath(), e.getMessage());
            }
        }
        logWarnings(result);
        if (result.exitCode() == 0) {
            diagnostics.stream().filter(Diagnostic::isWarningOrError).forEach(d -> getLogger().warn(d.toString()));
        }
        String failOn = getFailOn().get();
        Optional<GradleException> failure = ExitCodes.failure(
                invocation("check", executable),
                result,
                diagnostics.stream().filter(d -> d.meets(failOn)).map(Diagnostic::toString).toList(),
                report,
                "Fix them, or set adocfoundry.check.failOn = 'error' to fail on errors only.");
        if (failure.isPresent()) {
            throw failure.get();
        }
    }
}
