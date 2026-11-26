package io.github.bovinemagnet.adocfoundry.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class ToolVersionTest {

    @Test
    void parsesVersions() {
        assertThat(ToolVersion.parse("0.15.0")).isEqualTo(new ToolVersion(0, 15, 0));
        assertThat(ToolVersion.parse("1.2.3-dev")).isEqualTo(new ToolVersion(1, 2, 3));
        assertThat(ToolVersion.parse("0.15.0").toString()).isEqualTo("0.15.0");
    }

    @Test
    void parsesTheVersionLine() {
        assertThat(ToolVersion.fromVersionOutput("adocfoundry-cli 0.15.0\n")).isEqualTo(new ToolVersion(0, 15, 0));
        assertThatThrownBy(() -> ToolVersion.fromVersionOutput("something else"))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("something else");
    }

    @Test
    void comparesNumerically() {
        assertThat(ToolVersion.parse("0.9.0")).isLessThan(ToolVersion.parse("0.15.0"));
        assertThat(ToolVersion.parse("0.15.1")).isGreaterThan(ToolVersion.parse("0.15.0"));
        assertThat(ToolVersion.parse("1.0.0")).isGreaterThan(ToolVersion.parse("0.99.99"));
        assertThat(ToolVersion.MINIMUM).isEqualTo(ToolVersion.parse("0.15.0"));
    }

    @Test
    void refusesNonsense() {
        assertThatThrownBy(() -> ToolVersion.parse("latest")).isInstanceOf(GradleException.class);
    }
}
