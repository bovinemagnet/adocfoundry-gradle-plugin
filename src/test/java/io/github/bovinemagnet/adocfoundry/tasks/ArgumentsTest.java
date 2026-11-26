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
}
