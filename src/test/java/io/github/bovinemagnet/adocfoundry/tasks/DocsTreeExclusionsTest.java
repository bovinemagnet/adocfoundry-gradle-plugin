package io.github.bovinemagnet.adocfoundry.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** The cases of {@code is_ignored_beneath} in AdocFoundry's {@code adoc-core}, as folders. */
class DocsTreeExclusionsTest {

    private static final Predicate<String> DOCS_IS_A_COMPONENT = folder -> folder.equals("docs");

    private static boolean ignored(String folder) {
        return DocsTreeExclusions.isIgnored(folder.split("/"), folder.split("/").length, DOCS_IS_A_COMPONENT);
    }

    @Test
    void buildOutputNamesAreOrdinaryFoldersInsideModules() {
        assertThat(ignored("docs/modules/ROOT/pages/build")).isFalse();
        assertThat(ignored("docs/modules/ROOT/pages/dist/target")).isFalse();
        assertThat(ignored("build")).as("beside the component").isTrue();
        assertThat(ignored("docs/build")).as("in the component, outside its modules").isTrue();
        assertThat(ignored("docs/modules/build")).as("a module named build").isFalse();
        assertThat(ignored("docs/modules/ROOT/pages/node_modules")).isTrue();
        assertThat(ignored("docs/modules/ROOT/.git")).isTrue();
        assertThat(ignored("other/modules/ROOT/build")).as("not a component").isTrue();
        assertThat(ignored("build/docs/modules/ROOT/pages")).isTrue();
    }

    @Test
    void leavesOutEveryDefaultFolder() {
        for (String name : new String[] {".cache", ".git", ".zed", "node_modules", "build", "target", "dist", ".idea", ".gradle"}) {
            assertThat(ignored(name)).as(name).isTrue();
            assertThat(ignored("docs/modules/ROOT/" + name)).as(name + " in a module")
                    .isEqualTo(!name.equals("build") && !name.equals("target") && !name.equals("dist"));
        }
        assertThat(ignored("docs")).isFalse();
        assertThat(ignored(".github")).isFalse();
    }

    @Test
    void aFileIsJudgedByItsFolders() {
        String[] file = "docs/build".split("/");
        assertThat(DocsTreeExclusions.isIgnored(file, 1, DOCS_IS_A_COMPONENT)).as("a file named build").isFalse();
        assertThat(DocsTreeExclusions.isIgnored("build/page.adoc".split("/"), 1, DOCS_IS_A_COMPONENT)).isTrue();
    }
}
