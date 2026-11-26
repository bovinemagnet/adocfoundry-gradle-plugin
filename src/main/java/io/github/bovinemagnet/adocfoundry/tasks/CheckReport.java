package io.github.bovinemagnet.adocfoundry.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;

/**
 * Reads the bare JSON array {@code check --format json} prints. The format is not versioned yet
 * (AdocFoundry#807), so unknown fields are ignored and missing ones left empty.
 */
public final class CheckReport {

    private CheckReport() {}

    /**
     * Reads the report.
     *
     * @param json the CLI's standard output
     * @return the diagnostics, in the CLI's order
     * @throws GradleException when the output is not a JSON array
     */
    public static List<Diagnostic> parse(String json) {
        Object value = Json.parse(json);
        if (!(value instanceof List<?> entries)) {
            throw new GradleException("adocfoundry-cli check printed JSON that is not an array of diagnostics.");
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> fields) {
                diagnostics.add(new Diagnostic(
                        text(fields.get("path")),
                        number(fields.get("line")),
                        number(fields.get("column")),
                        text(fields.get("severity")),
                        text(fields.get("code")),
                        text(fields.get("message"))));
            }
        }
        return diagnostics;
    }

    static String text(Object value) {
        return value instanceof String string ? string : "";
    }

    static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
