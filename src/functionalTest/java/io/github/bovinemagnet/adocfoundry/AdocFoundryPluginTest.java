package io.github.bovinemagnet.adocfoundry;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.io.TempDir;

class AdocFoundryPluginTest {

    @TempDir
    Path root;

    @GradleVersionsTest
    void pluginApplies(String gradleVersion) {
        TestProject project = new TestProject(root, gradleVersion).buildFile("""
                tasks.register('showToolVersion') {
                    def toolVersion = adocfoundry.toolVersion
                    doLast { println "toolVersion=${toolVersion.get()}" }
                }
                """);

        for (BuildResult result : project.runTwice("showToolVersion")) {
            assertThat(result.getOutput()).contains("toolVersion=0.15.0");
        }
    }
}
