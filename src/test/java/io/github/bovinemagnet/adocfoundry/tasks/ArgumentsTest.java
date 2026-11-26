package io.github.bovinemagnet.adocfoundry.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
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
}
