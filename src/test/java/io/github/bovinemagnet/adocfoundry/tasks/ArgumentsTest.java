package io.github.bovinemagnet.adocfoundry.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArgumentsTest {

    private static final File WORKSPACE = new File("/work/docs-project").getAbsoluteFile();

    @Test
    void check() {
        assertThat(CheckTask.arguments(WORKSPACE, "warning"))
                .containsExactly("check", WORKSPACE.getPath(), "--format", "json", "--fail-on", "warning");
    }

    @Test
    void auditPassesOnlyWhatIsSet() {
        File json = new File(WORKSPACE, "build/audit.json");

        assertThat(AuditTask.arguments(WORKSPACE, "json", json, null, null, null))
                .containsExactly("audit", WORKSPACE.getPath(), "--format", "json", "--output", json.getPath());
        assertThat(AuditTask.arguments(WORKSPACE, "json", json, null, null, false))
                .doesNotContain("--require-complete");
        assertThat(AuditTask.arguments(WORKSPACE, "sarif", json, "2.0@manual", "warning", true))
                .containsExactly("audit", WORKSPACE.getPath(), "--format", "sarif", "--output", json.getPath(),
                        "--component", "2.0@manual", "--fail-on", "warning", "--require-complete");
    }

    private static final File OUTPUT = new File(WORKSPACE, "build/adocfoundry/pdf/manual.pdf");

    @Test
    void pdfOfComponentLeavesTheLookToTheWorkspace() {
        assertThat(PdfTask.arguments(WORKSPACE, new PdfTask.Selector("2.0@manual", null, null), null, null,
                List.of(), "none", OUTPUT))
                .containsExactly("pdf", WORKSPACE.getPath(), "--root", WORKSPACE.getPath(),
                        "--component", "2.0@manual", "--output", OUTPUT.getPath(), "--fail-on", "none");
    }

    @Test
    void pdfOfPage() {
        File page = new File(WORKSPACE, "docs/modules/ROOT/pages/index.adoc");

        assertThat(PdfTask.arguments(WORKSPACE, new PdfTask.Selector(null, page, null), null, null,
                List.of(), "warning", OUTPUT))
                .containsExactly("pdf", page.getPath(), "--root", WORKSPACE.getPath(),
                        "--output", OUTPUT.getPath(), "--fail-on", "warning");
    }

    @Test
    void pdfOfProfile() {
        assertThat(PdfTask.arguments(WORKSPACE, new PdfTask.Selector(null, null, "handbook"), null, null,
                List.of(), "none", OUTPUT))
                .containsExactly("pdf", "--profile", "handbook", WORKSPACE.getPath(),
                        "--output", OUTPUT.getPath(), "--fail-on", "none");
    }

    @Test
    void pdfPassesThemeOnlyWhenSet() {
        File fonts = new File(WORKSPACE, "fonts");

        assertThat(PdfTask.arguments(WORKSPACE, new PdfTask.Selector("manual", null, null), null, null,
                List.of(), "none", OUTPUT))
                .doesNotContain("--theme", "--fonts-dir", "--standard");
        assertThat(PdfTask.arguments(WORKSPACE, new PdfTask.Selector("manual", null, null), "acme", fonts,
                List.of("pdf-a-2a", "pdf-ua-1"), "none", OUTPUT))
                .containsSubsequence("--theme", "acme")
                .containsSubsequence("--fonts-dir", fonts.getPath())
                .containsSubsequence("--standard", "pdf-a-2a", "--standard", "pdf-ua-1");
    }

    @Test
    void themeFilesAreMadeAbsolute() {
        File projectDir = WORKSPACE;

        assertThat(PdfTask.themeArgument("acme", projectDir)).isEqualTo("acme");
        assertThat(PdfTask.themeArgument("src/docs/themes/manual-theme.yml", projectDir))
                .isEqualTo(new File(projectDir, "src/docs/themes/manual-theme.yml").getPath());
        assertThat(PdfTask.themeArgument(OUTPUT.getPath() + ".yml", projectDir)).isEqualTo(OUTPUT.getPath() + ".yml");
    }

    @Test
    void selectorMustBeExactlyOne() {
        assertThat(new PdfTask.Selector(null, null, null).problem()).contains("sets none");
        assertThat(new PdfTask.Selector("a", null, "b").problem()).contains("sets component and profile");
        assertThat(new PdfTask.Selector("a", new File("x"), "b").problem()).contains("sets component, page and profile");
        assertThat(new PdfTask.Selector("a", null, null).problem()).isNull();
    }
}
