package io.github.bovinemagnet.adocfoundry;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.io.TempDir;

class PublishingTest {

    @TempDir
    Path root;

    /** The plugin marker and jar, published to a local repository, apply by id and version. */
    @GradleVersionsTest
    void pluginMarkerIsPublishedLocally(String gradleVersion) {
        Path repository = Path.of(System.getProperty("adocfoundry.publishedRepository"));
        assertThat(repository.resolve("io/github/bovinemagnet/adocfoundry/"
                + "io.github.bovinemagnet.adocfoundry.gradle.plugin/0.0.0")).isDirectory();
        TestProject project = new TestProject(root, gradleVersion).withoutPluginClasspath();
        project.write("settings.gradle", """
                pluginManagement {
                    repositories {
                        maven { url = uri('%s') }
                    }
                }
                rootProject.name = 'consumer'
                """.formatted(TestProject.groovyString(repository.toUri().toString())));
        project.write("build.gradle", """
                plugins {
                    id 'io.github.bovinemagnet.adocfoundry' version '0.0.0'
                }

                tasks.register('showToolVersion') {
                    def toolVersion = adocfoundry.toolVersion
                    doLast { println "toolVersion=${toolVersion.get()}" }
                }
                """);

        for (BuildResult result : project.runTwice("showToolVersion", "tasks")) {
            assertThat(result.getOutput()).contains("toolVersion=0.15.0").contains("adocfoundryCheck");
        }
    }
}
