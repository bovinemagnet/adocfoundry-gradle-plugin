package io.github.bovinemagnet.adocfoundry.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class CheckReportTest {

    /** What adocfoundry-cli 0.15.0 printed for the broken-xref fixture, plus a field it may add later. */
    private static final String SAMPLE = """
            [
              {
                "path": "demo/modules/ROOT/pages/index.adoc",
                "range": {
                  "start": 77,
                  "end": 103
                },
                "line": 5,
                "column": 5,
                "severity": "warning",
                "code": "adoc.unresolved-xref-file",
                "message": "Unresolved AsciiDoc xref target: missing.adoc",
                "future": [true, null, 1.5e3, {"nested": "\\u00e9\\"quoted\\""}]
              }
            ]
            """;

    @Test
    void readsTheSample() {
        List<Diagnostic> diagnostics = CheckReport.parse(SAMPLE);

        assertThat(diagnostics).containsExactly(new Diagnostic(
                "demo/modules/ROOT/pages/index.adoc", 5, 5, "warning", "adoc.unresolved-xref-file",
                "Unresolved AsciiDoc xref target: missing.adoc"));
        assertThat(diagnostics.get(0).toString()).isEqualTo(
                "demo/modules/ROOT/pages/index.adoc:5:5: warning adoc.unresolved-xref-file"
                        + " Unresolved AsciiDoc xref target: missing.adoc");
    }

    @Test
    void readsAnEmptyReport() {
        assertThat(CheckReport.parse("[]\n")).isEmpty();
    }

    @Test
    void toleratesMissingFields() {
        assertThat(CheckReport.parse("[{\"path\": \"a.adoc\", \"severity\": \"error\"}]"))
                .containsExactly(new Diagnostic("a.adoc", 0, 0, "error", "", ""));
    }

    @Test
    void refusesWhatIsNotAnArray() {
        assertThatThrownBy(() -> CheckReport.parse("{\"schema_version\": 2}"))
                .isInstanceOf(GradleException.class);
        assertThatThrownBy(() -> CheckReport.parse("[{"))
                .isInstanceOf(GradleException.class);
    }

    @Test
    void readsJsonValues() {
        assertThat(Json.parse("{\"a\": [1, -2.5, true, false, null, \"x\\ny\\t\\\\\\/\"]}"))
                .isEqualTo(java.util.Map.of("a", java.util.Arrays.asList(1L, -2.5, true, false, null, "x\ny\t\\/")));
    }
}
