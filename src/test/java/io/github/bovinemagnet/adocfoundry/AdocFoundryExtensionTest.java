package io.github.bovinemagnet.adocfoundry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdocFoundryExtensionTest {

    @TempDir
    File projectDir;

    @Test
    void extensionHasDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
        project.getPluginManager().apply("io.github.bovinemagnet.adocfoundry");

        AdocFoundryExtension extension = project.getExtensions().getByType(AdocFoundryExtension.class);

        assertThat(extension.getToolVersion().get()).isEqualTo("0.15.0");
        assertThat(extension.getExecutable().isPresent()).isFalse();
        assertThat(extension.getBaseUrl().get())
                .isEqualTo("https://github.com/bovinemagnet/AdocFoundry/releases/download");
        assertThat(extension.getWorkspaceDir().get().getAsFile()).isEqualTo(project.getProjectDir());
        assertThat(extension.getWarnAboutUserSettings().get()).isTrue();
    }
}
