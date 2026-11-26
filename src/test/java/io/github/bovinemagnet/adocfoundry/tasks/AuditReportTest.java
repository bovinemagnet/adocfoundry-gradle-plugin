package io.github.bovinemagnet.adocfoundry.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditReportTest {

    /** Trimmed from what adocfoundry-cli 0.15.0 wrote for the broken-xref fixture. */
    private static final String FAILING = """
            {
              "schema_version": 1,
              "scope": {"kind": "workspace"},
              "fail_on": "warning",
              "completeness": {"status": "complete"},
              "findings": [
                {
                  "code": "adoc.unresolved-xref-file",
                  "severity": "warning",
                  "message": "Unresolved AsciiDoc xref target: missing.adoc",
                  "location": {"root": 0, "path": "demo/modules/ROOT/pages/index.adoc", "range": {"start": 77, "end": 103}},
                  "coordinate": {"component": "demo", "version": "latest"}
                }
              ]
            }
            """;

    private static final String UNAVAILABLE = """
            {
              "schema_version": 1,
              "completeness": {
                "status": "unavailable",
                "gaps": [{"reason": "remote-component-missing", "detail": "Component `nope` is not in the local catalog"}]
              },
              "findings": []
            }
            """;

    @Test
    void readsFindings() {
        AuditReport report = AuditReport.parse(FAILING);

        assertThat(report.findings()).containsExactly(new Diagnostic(
                "demo/modules/ROOT/pages/index.adoc", 0, 0, "warning", "adoc.unresolved-xref-file",
                "Unresolved AsciiDoc xref target: missing.adoc"));
        assertThat(report.findings().get(0).toString()).isEqualTo(
                "demo/modules/ROOT/pages/index.adoc: warning adoc.unresolved-xref-file"
                        + " Unresolved AsciiDoc xref target: missing.adoc");
        assertThat(report.failOn()).isEqualTo("warning");
        assertThat(report.completeness()).isEqualTo("Audit completeness: complete");
    }

    @Test
    void readsGaps() {
        assertThat(AuditReport.parse(UNAVAILABLE).completeness()).isEqualTo(
                "Audit completeness: unavailable\n  remote-component-missing: Component `nope` is not in the local catalog");
    }
}
