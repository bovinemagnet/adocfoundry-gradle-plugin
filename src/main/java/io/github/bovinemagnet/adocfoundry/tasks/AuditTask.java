package io.github.bovinemagnet.adocfoundry.tasks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Runs {@code adocfoundry-cli audit} twice, once per report format. The first run, writing JSON,
 * decides the result; the second writes SARIF with {@code --fail-on none}.
 */
public abstract class AuditTask extends AdocFoundryTask {

    /**
     * @return {@code [<version>@]<name>} to audit, or unset for the whole workspace
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getComponent();

    /**
     * @return {@code none}, {@code warning} or {@code error}; unset, the workspace's
     *     {@code [quality]} table decides
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getFailOn();

    /**
     * @return whether a partial audit fails; unset, the workspace's {@code [quality]} table decides
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<Boolean> getRequireComplete();

    /**
     * @return the JSON report
     */
    @OutputFile
    public abstract RegularFileProperty getJsonReport();

    /**
     * @return the SARIF 2.1.0 report
     */
    @OutputFile
    public abstract RegularFileProperty getSarifReport();

    /**
     * The CLI arguments. Options left {@code null} are not passed, so the workspace decides.
     *
     * @param workspace the workspace root
     * @param format {@code json} or {@code sarif}
     * @param output the report file
     * @param component the component, or {@code null}
     * @param failOn the threshold, or {@code null}
     * @param requireComplete whether completeness is required, or {@code null}
     * @return the arguments
     */
    static List<String> arguments(File workspace, String format, File output, String component, String failOn,
            Boolean requireComplete) {
        List<String> arguments = new ArrayList<>(List.of(
                "audit", workspace.getAbsolutePath(), "--format", format, "--output", output.getAbsolutePath()));
        if (component != null) {
            arguments.add("--component");
            arguments.add(component);
        }
        if (failOn != null) {
            arguments.add("--fail-on");
            arguments.add(failOn);
        }
        if (Boolean.TRUE.equals(requireComplete)) {
            arguments.add("--require-complete");
        }
        return arguments;
    }

    /** Runs the audit. */
    @TaskAction
    public void audit() {
        File workspace = getWorkspaceDir().get().getAsFile();
        File json = getJsonReport().get().getAsFile();
        File sarif = getSarifReport().get().getAsFile();
        String component = getComponent().getOrNull();
        Boolean requireComplete = getRequireComplete().getOrNull();
        try {
            // A run that fails before writing must not leave an older report looking current.
            Files.deleteIfExists(json.toPath());
            Files.deleteIfExists(sarif.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Path executable = executable();
        CliResult verdict = run(executable,
                arguments(workspace, "json", json, component, getFailOn().getOrNull(), requireComplete));
        logWarnings(verdict);
        if (verdict.exitCode() > 2) {
            throw ExitCodes.failure(invocation("audit", executable), verdict, List.of(), json, "").orElseThrow();
        }

        AuditReport report = json.isFile() ? read(json) : null;
        if (report != null && verdict.exitCode() == 2 && verdict.stderr().isBlank()) {
            verdict = new CliResult(2, verdict.stdout(), report.completeness());
        }
        if (verdict.exitCode() == 2) {
            throw ExitCodes.failure(invocation("audit", executable), verdict, List.of(), json, "").orElseThrow();
        }

        CliResult sarifRun = run(executable, arguments(workspace, "sarif", sarif, component, "none", requireComplete));
        logWarnings(sarifRun);

        List<String> findings = report == null
                ? List.of()
                : report.findings().stream().filter(d -> d.meets(report.failOn())).map(Diagnostic::toString).toList();
        Optional<GradleException> failure = ExitCodes.failure(invocation("audit", executable), verdict, findings, json,
                "Fix them, or change adocfoundry.audit.failOn or the [quality] table of .adocfoundry.toml.");
        if (failure.isEmpty()) {
            failure = ExitCodes.failure(invocation("audit", executable), sarifRun, List.of(), sarif, "");
        }
        if (failure.isPresent()) {
            throw failure.get();
        }
    }

    private AuditReport read(File json) {
        try {
            return AuditReport.parse(Files.readString(json.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | GradleException e) {
            getLogger().warn("{}: could not read {}: {}", getPath(), json, e.getMessage());
            return null;
        }
    }
}
