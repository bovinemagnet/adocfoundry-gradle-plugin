package io.github.bovinemagnet.adocfoundry;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bovinemagnet.adocfoundry.tool.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

/** The {@code pdfs} container and its tasks, against the real CLI over the {@code themed} fixture. */
@Tag("network")
class PdfTaskTest {

    private static final String ENTRIES = """
            adocfoundry {
                pdfs {
                    manual {
                        component = 'demo'
                    }
                    intro {
                        page = file('demo/modules/ROOT/pages/index.adoc')
                    }
                    guide {
                        profile = 'guide'
                    }
                }
            }
            """;

    @TempDir
    Path root;

    private TestProject themed(String gradleVersion, String body) {
        return new TestProject(root, gradleVersion).fixture("themed").buildFile(body);
    }

    private static Path pdf(TestProject project, String name) {
        return project.dir().resolve("build/adocfoundry/pdf/" + name + ".pdf");
    }

    private static void assertIsPdf(Path file) throws IOException {
        assertThat(file).isRegularFile();
        byte[] bytes = Files.readAllBytes(file);
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @GradleVersionsTest
    void pdfOfComponent(String gradleVersion) throws IOException {
        TestProject project = themed(gradleVersion, ENTRIES);

        BuildResult result = project.runTwice("adocfoundryPdfManual").get(0);

        assertThat(result.task(":adocfoundryPdfManual").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).containsPattern("manual\\.pdf \\(\\d+ pages\\)");
        assertIsPdf(pdf(project, "manual"));
    }

    @GradleVersionsTest
    void pdfOfPage(String gradleVersion) throws IOException {
        TestProject project = themed(gradleVersion, ENTRIES);

        BuildResult result = project.runTwice("adocfoundryPdfIntro").get(0);

        assertThat(result.getOutput()).contains("intro.pdf (1 pages)");
        assertIsPdf(pdf(project, "intro"));
    }

    @GradleVersionsTest
    void pdfOfProfile(String gradleVersion) throws IOException {
        TestProject project = themed(gradleVersion, ENTRIES);

        project.runTwice("adocfoundryPdfGuide");

        assertIsPdf(pdf(project, "guide"));
        // The profile's own output is replaced by the plugin's, so nothing is written there.
        assertThat(project.dir().resolve("guide.pdf")).doesNotExist();
    }

    @GradleVersionsTest
    void pdfEntryNeedsOneSelector(String gradleVersion) {
        TestProject project = themed(gradleVersion, """
                adocfoundry {
                    pdfs {
                        nothing {
                        }
                        both {
                            component = 'demo'
                            profile = 'guide'
                        }
                    }
                }
                """);

        for (int run = 0; run < 2; run++) {
            assertThat(project.runAndFail("adocfoundryPdfNothing").getOutput())
                    .contains("adocfoundry.pdfs.nothing")
                    .contains("exactly one of component, page and profile")
                    .contains("sets none");
        }
        assertThat(project.runAndFail("adocfoundryPdfBoth").getOutput())
                .contains("adocfoundry.pdfs.both")
                .contains("sets component and profile");
    }

    @GradleVersionsTest
    void pdfIsUpToDateSecondTime(String gradleVersion) {
        TestProject project = themed(gradleVersion, ENTRIES);

        List<BuildResult> runs = project.runTwice("adocfoundryPdf");

        for (String task : List.of(":adocfoundryPdfManual", ":adocfoundryPdfIntro", ":adocfoundryPdfGuide")) {
            assertThat(runs.get(0).task(task).getOutcome()).as(task).isEqualTo(TaskOutcome.SUCCESS);
            assertThat(runs.get(1).task(task).getOutcome()).as(task).isEqualTo(TaskOutcome.UP_TO_DATE);
        }
        // The theme lives in the workspace, so changing it reruns the export.
        project.write("themes/acme-theme.yml", "extends: default\npage:\n  size: A6\n");
        assertThat(project.run("adocfoundryPdfManual").task(":adocfoundryPdfManual").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    @GradleVersionsTest
    void pdfOutputUnderBuildDir(String gradleVersion) throws IOException {
        TestProject project = themed(gradleVersion, ENTRIES + """
                adocfoundry.pdfs.manual.output = layout.buildDirectory.file('books/handbook.pdf')
                """);

        project.runTwice("adocfoundryPdf");

        assertIsPdf(project.dir().resolve("build/books/handbook.pdf"));
        assertIsPdf(pdf(project, "intro"));
        assertIsPdf(pdf(project, "guide"));
        assertThat(pdf(project, "manual")).doesNotExist();
    }

    @GradleVersionsTest
    void pdfMatchesAdocFoundry(String gradleVersion) throws Exception {
        TestProject project = themed(gradleVersion, ENTRIES);
        project.runTwice("adocfoundryPdf");
        Path direct = root.resolve("direct");

        // The CLI as a writer runs it in the workspace, which is what the app exports.
        assertThat(pdf(project, "manual")).hasSameBinaryContentAs(
                cli(project, "pdf", "demo", "--output", direct.resolve("manual.pdf").toString()));
        assertThat(pdf(project, "intro")).hasSameBinaryContentAs(
                cli(project, "pdf", "demo/modules/ROOT/pages/index.adoc", "--output", direct.resolve("intro.pdf").toString()));
        assertThat(pdf(project, "guide")).hasSameBinaryContentAs(
                cli(project, "pdf", "--profile", "guide", "--output", direct.resolve("guide.pdf").toString()));
    }

    @GradleVersionsTest
    void pdfUsesWorkspaceTheme(String gradleVersion) throws Exception {
        TestProject project = themed(gradleVersion, ENTRIES);
        project.runTwice("adocfoundryPdfManual");

        Path bundledDefault = cli(project, "pdf", "demo", "--theme", "default",
                "--output", root.resolve("direct/default.pdf").toString());

        assertThat(Files.readAllBytes(pdf(project, "manual"))).isNotEqualTo(Files.readAllBytes(bundledDefault));
    }

    @GradleVersionsTest
    void pdfPassesThemeOnlyWhenSet(String gradleVersion) throws Exception {
        TestProject project = themed(gradleVersion, ENTRIES + """
                adocfoundry.pdfs.manual.theme = 'default'
                """);
        project.runTwice("adocfoundryPdfManual");

        assertThat(pdf(project, "manual")).hasSameBinaryContentAs(cli(project, "pdf", "demo", "--theme", "default",
                "--output", root.resolve("direct/default.pdf").toString()));
        assertThat(Files.readAllBytes(pdf(project, "manual"))).isNotEqualTo(Files.readAllBytes(
                cli(project, "pdf", "demo", "--output", root.resolve("direct/workspace.pdf").toString())));
    }

    @GradleVersionsTest
    void warnsWhenUserSettingsSetPdf(String gradleVersion) throws Exception {
        String warning = "AdocFoundry's user settings";
        TestProject project = themed(gradleVersion, ENTRIES);

        assertThat(project.run("adocfoundryPdfManual").getOutput()).doesNotContain(warning);

        Path settings = project.userSettingsFile();
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "[ui]\nfont_size = 14\n\n[pdf]\ntheme = \"default-sans\"\n");
        Files.delete(pdf(project, "manual"));
        BuildResult warned = project.run("adocfoundryPdfManual");
        assertThat(warned.getOutput()).contains(warning).contains("warnAboutUserSettings");
        // The CLI does not read the user settings, so the PDF is the workspace's all the same.
        assertThat(pdf(project, "manual")).hasSameBinaryContentAs(
                cli(project, "pdf", "demo", "--output", root.resolve("direct/manual.pdf").toString()));

        project.buildFile(ENTRIES + "adocfoundry.warnAboutUserSettings = false\n");
        Files.delete(pdf(project, "manual"));
        assertThat(project.run("adocfoundryPdfManual").getOutput()).doesNotContain(warning);
    }

    /** Runs the CLI the plugin used, in the workspace, with the test's user config directory. */
    private static Path cli(TestProject project, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(cliBinary().toString());
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(project.dir().toFile()).inheritIO();
        Map<String, String> environment = builder.environment();
        environment.put("XDG_CONFIG_HOME", project.userConfigDir().toString());
        environment.put("APPDATA", project.userConfigDir().toString());
        environment.put("HOME", project.userConfigDir().resolve("home").toString());
        Process process = builder.start();
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).isTrue();
        assertThat(process.exitValue()).as(String.join(" ", command)).isZero();
        int output = command.indexOf("--output");
        return Path.of(command.get(output + 1));
    }

    private static Path cliBinary() {
        String local = System.getenv("ADOCFOUNDRY_CLI");
        if (local != null && !local.isEmpty()) {
            return Path.of(local);
        }
        Platform platform = Platform.current();
        return Path.of(System.getProperty("adocfoundry.testKitDir"), "caches", "adocfoundry",
                AdocFoundryPlugin.DEFAULT_TOOL_VERSION, platform.id(), platform.executableName());
    }
}
