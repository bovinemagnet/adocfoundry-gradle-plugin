package io.github.bovinemagnet.adocfoundry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

/** {@code adocfoundryAudit} against the real CLI. */
@Tag("network")
class AuditTaskTest {

    @TempDir
    Path root;

    @GradleVersionsTest
    void auditWritesJsonAndSarif(String gradleVersion) throws IOException {
        TestProject project = new TestProject(root, gradleVersion).fixture("clean").buildFile("");

        List<BuildResult> runs = project.runTwice("adocfoundryAudit");

        assertThat(runs.get(0).task(":adocfoundryAudit").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(runs.get(1).task(":adocfoundryAudit").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(Files.readString(project.dir().resolve("build/reports/adocfoundry/audit.json")))
                .contains("\"schema_version\": 1")
                .contains("\"status\": \"complete\"");
        assertThat(Files.readString(project.dir().resolve("build/reports/adocfoundry/audit.sarif")))
                .contains("\"version\": \"2.1.0\"");
        // Not part of the lifecycle: check does not run it.
        assertThat(project.run("check").task(":adocfoundryAudit")).isNull();
    }

    @GradleVersionsTest
    void auditFailsOnThreshold(String gradleVersion) throws IOException {
        TestProject project = new TestProject(root, gradleVersion).fixture("broken-xref").buildFile("");
        assertThat(project.runTwice("adocfoundryAudit").get(0).task(":adocfoundryAudit").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        project.buildFile("""
                adocfoundry {
                    audit {
                        failOn = 'warning'
                    }
                }
                """);
        for (int run = 0; run < 2; run++) {
            BuildResult failed = project.runAndFail("adocfoundryAudit");
            assertThat(failed.task(":adocfoundryAudit").getOutcome()).isEqualTo(TaskOutcome.FAILED);
            assertThat(failed.getOutput())
                    .contains(":adocfoundryAudit")
                    .contains("adocfoundry-cli audit exited with 1")
                    .contains("demo/modules/ROOT/pages/index.adoc: warning adoc.unresolved-xref-file")
                    .contains("audit.json");
        }
        // Both reports are written, the SARIF one included, though the audit failed.
        assertThat(Files.readString(project.dir().resolve("build/reports/adocfoundry/audit.json")))
                .contains("\"fail_on\": \"warning\"");
        assertThat(Files.readString(project.dir().resolve("build/reports/adocfoundry/audit.sarif")))
                .contains("adoc.unresolved-xref-file");
    }

    @GradleVersionsTest
    void auditRequireCompletePassesThrough(String gradleVersion) throws IOException {
        TestProject project = new TestProject(root, gradleVersion).fixture("clean").buildFile("""
                adocfoundry {
                    audit {
                        component = 'latest@demo'
                        requireComplete = true
                    }
                }
                """);

        project.runTwice("adocfoundryAudit");

        String report = Files.readString(project.dir().resolve("build/reports/adocfoundry/audit.json"));
        assertThat(report).contains("\"require_complete\": true").contains("\"kind\": \"component\"");

        project.buildFile("""
                adocfoundry {
                    audit {
                        component = 'nope'
                        requireComplete = true
                    }
                }
                """);
        BuildResult failed = project.runAndFail("adocfoundryAudit");
        assertThat(failed.getOutput())
                .contains("could not run the request (exit 2)")
                .contains("Component `nope` is not in the local catalog");
    }
}
