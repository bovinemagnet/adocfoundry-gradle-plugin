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

/** {@code adocfoundryCheck} against the real CLI. */
@Tag("network")
class CheckTaskTest {

    @TempDir
    Path root;

    private TestProject project(String gradleVersion, String fixture, String body) {
        return new TestProject(root, gradleVersion).fixture(fixture).buildFile(body);
    }

    @GradleVersionsTest
    void checkPassesCleanDocs(String gradleVersion) throws IOException {
        TestProject project = project(gradleVersion, "clean", "");

        List<BuildResult> runs = project.runTwice("check");

        assertThat(runs.get(0).task(":adocfoundryCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(project.dir().resolve("build/reports/adocfoundry/check.json")).strip())
                .isEqualTo("[]");
    }

    @GradleVersionsTest
    void checkFailsOnWarningWhenAsked(String gradleVersion) throws IOException {
        TestProject passing = project(gradleVersion, "broken-xref", "");

        BuildResult passed = passing.runTwice("adocfoundryCheck").get(0);
        assertThat(passed.task(":adocfoundryCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(passed.getOutput()).contains("demo/modules/ROOT/pages/index.adoc:5:5: warning adoc.unresolved-xref-file");

        TestProject failing = passing.buildFile("""
                adocfoundry {
                    check {
                        failOn = 'warning'
                    }
                }
                """);
        for (int run = 0; run < 2; run++) {
            BuildResult failed = failing.runAndFail("adocfoundryCheck");
            assertThat(failed.task(":adocfoundryCheck").getOutcome()).isEqualTo(TaskOutcome.FAILED);
            assertThat(failed.getOutput())
                    .contains(":adocfoundryCheck")
                    .contains("exited with 1")
                    .contains("demo/modules/ROOT/pages/index.adoc:5:5")
                    .contains("adoc.unresolved-xref-file")
                    .contains("build/reports/adocfoundry/check.json".replace('/', java.io.File.separatorChar));
            if (run == 1) {
                assertThat(failed.getOutput()).contains("Reusing configuration cache.");
            }
        }
        assertThat(Files.readString(failing.dir().resolve("build/reports/adocfoundry/check.json")))
                .contains("adoc.unresolved-xref-file");
    }

    @GradleVersionsTest
    void checkIsUpToDateSecondTime(String gradleVersion) throws IOException {
        TestProject project = project(gradleVersion, "clean", "");

        List<BuildResult> runs = project.runTwice("adocfoundryCheck");

        assertThat(runs.get(0).task(":adocfoundryCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(runs.get(1).task(":adocfoundryCheck").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        // Content changes rerun it; the CLI's cache, the build folder and Gradle's own do not.
        project.write("demo/modules/ROOT/pages/index.adoc", "= Documentation Home\n\nChanged.\n");
        assertThat(project.run("adocfoundryCheck").task(":adocfoundryCheck").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);
        project.write(".cache/adocfoundry/diagrams/x.svg", "<svg/>");
        project.write("node_modules/x/index.adoc", "= Ignored\n");
        project.write("demo/build/out.adoc", "= Ignored\n");
        assertThat(project.run("adocfoundryCheck").task(":adocfoundryCheck").getOutcome())
                .isEqualTo(TaskOutcome.UP_TO_DATE);
        project.write("demo/modules/build/pages/new.adoc", "= Content in a module named build\n");
        assertThat(project.run("adocfoundryCheck").task(":adocfoundryCheck").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    @GradleVersionsTest
    void checkWorksWithConfigurationCache(String gradleVersion) {
        TestProject project = project(gradleVersion, "clean", "");

        List<BuildResult> runs = project.runTwice("check");

        assertThat(runs.get(0).getOutput()).contains("Configuration cache entry stored.");
        assertThat(runs.get(1).getOutput()).contains("Configuration cache entry reused.");
        assertThat(runs.get(1).task(":adocfoundryCheck").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
    }
}
