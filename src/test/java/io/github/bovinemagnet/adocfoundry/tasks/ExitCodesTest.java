package io.github.bovinemagnet.adocfoundry.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bovinemagnet.adocfoundry.tool.Platform;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class ExitCodesTest {

    private static final Invocation CHECK = new Invocation(
            ":adocfoundryCheck", "check", Path.of("/opt/adocfoundry-cli"), Platform.of("Linux", "amd64"));
    private static final File REPORT = new File("/work/build/reports/adocfoundry/check.json");

    @Test
    void zeroSucceeds() {
        assertThat(ExitCodes.failure(CHECK, new CliResult(0, "[]", ""), List.of(), REPORT, "Fix them.")).isEmpty();
    }

    @Test
    void oneListsTheDiagnosticsAndTheReport() {
        GradleException failure = ExitCodes.failure(CHECK, new CliResult(1, "", ""),
                List.of("a.adoc:1:1: warning x One", "b.adoc:2:3: error y Two"), REPORT,
                "Fix them, or raise failOn.").orElseThrow();

        assertThat(failure.getMessage())
                .contains(":adocfoundryCheck")
                .contains("adocfoundry-cli check exited with 1")
                .contains("  a.adoc:1:1: warning x One\n  b.adoc:2:3: error y Two")
                .contains("Full report: " + REPORT)
                .endsWith("Fix them, or raise failOn.");
    }

    @Test
    void oneListsTwentyThenCounts() {
        List<String> diagnostics = new ArrayList<>();
        for (int i = 1; i <= 23; i++) {
            diagnostics.add("page" + i + ".adoc:1:1: warning x");
        }

        String message = ExitCodes.failure(CHECK, new CliResult(1, "", ""), diagnostics, REPORT, "Fix them.")
                .orElseThrow().getMessage();

        assertThat(message).contains("page20.adoc").doesNotContain("page21.adoc").contains("and 3 more");
    }

    @Test
    void oneWithoutDiagnosticsShowsStandardError() {
        String message = ExitCodes.failure(CHECK, new CliResult(1, "", "the export failed\n"), List.of(), null, "")
                .orElseThrow().getMessage();

        assertThat(message).contains("the export failed").doesNotContain("Full report");
    }

    @Test
    void twoGivesStandardErrorInFull() {
        String stderr = "adocfoundry-cli: unknown --fail-on level `bogus`\nsecond line\n";

        String message = ExitCodes.failure(CHECK, new CliResult(2, "", stderr), List.of(), REPORT, "Fix them.")
                .orElseThrow().getMessage();

        assertThat(message)
                .contains(":adocfoundryCheck")
                .contains("could not run the request")
                .contains("exit 2")
                .contains("unknown --fail-on level `bogus`\nsecond line");
    }

    @Test
    void anythingElseNamesTheExecutableAndPlatform() {
        String message = ExitCodes.failure(CHECK, new CliResult(139, "", "Segmentation fault"), List.of(), REPORT, "")
                .orElseThrow().getMessage();

        assertThat(message)
                .contains(":adocfoundryCheck")
                .contains("exited with 139")
                .contains(File.separator + "opt" + File.separator + "adocfoundry-cli")
                .contains("linux-x86_64")
                .contains("glibc 2.35")
                .contains("Segmentation fault");
    }
}
