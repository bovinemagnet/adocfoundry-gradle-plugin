package io.github.bovinemagnet.adocfoundry.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;

/**
 * What a failure message needs from {@code audit --format json} ({@code schema_version} 1): the
 * findings, and the completeness with its gaps. With {@code --output}, the CLI prints neither to
 * standard error, so they are read from the report.
 *
 * @param findings the findings, located by file only, as the report gives byte offsets
 * @param failOn the threshold in force, from the build or the workspace's {@code [quality]}
 * @param completeness the completeness status and gaps, as lines of text
 */
public record AuditReport(List<Diagnostic> findings, String failOn, String completeness) {

    /**
     * Reads the report.
     *
     * @param json the report
     * @return the findings and completeness
     * @throws GradleException when the report is not a JSON object
     */
    public static AuditReport parse(String json) {
        if (!(Json.parse(json) instanceof Map<?, ?> report)) {
            throw new GradleException("adocfoundry-cli audit wrote a report that is not a JSON object.");
        }
        List<Diagnostic> findings = new ArrayList<>();
        if (report.get("findings") instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> finding) {
                    Object location = finding.get("location");
                    String path = location instanceof Map<?, ?> where ? CheckReport.text(where.get("path")) : "";
                    findings.add(new Diagnostic(path, 0, 0,
                            CheckReport.text(finding.get("severity")),
                            CheckReport.text(finding.get("code")),
                            CheckReport.text(finding.get("message"))));
                }
            }
        }
        StringBuilder completeness = new StringBuilder("Audit completeness:");
        if (report.get("completeness") instanceof Map<?, ?> status) {
            completeness.append(' ').append(CheckReport.text(status.get("status")));
            if (status.get("gaps") instanceof List<?> gaps) {
                for (Object gap : gaps) {
                    if (gap instanceof Map<?, ?> fields) {
                        completeness.append("\n  ").append(CheckReport.text(fields.get("reason")))
                                .append(": ").append(CheckReport.text(fields.get("detail")));
                    }
                }
            }
        }
        return new AuditReport(findings, CheckReport.text(report.get("fail_on")), completeness.toString());
    }
}
