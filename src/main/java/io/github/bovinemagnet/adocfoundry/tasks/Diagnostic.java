package io.github.bovinemagnet.adocfoundry.tasks;

/**
 * One diagnostic from {@code check --format json}.
 *
 * @param path the file, relative to the workspace root, with {@code /}
 * @param line the one-based line, or 0 when not given
 * @param column the one-based column in characters, or 0 when not given
 * @param severity {@code info}, {@code warning} or {@code error}
 * @param code the diagnostic's code, such as {@code adoc.unresolved-xref-file}
 * @param message the message
 */
public record Diagnostic(String path, long line, long column, String severity, String code, String message) {

    /**
     * Whether the diagnostic is a warning or an error, so worth showing in the build's log.
     *
     * @return {@code true} unless it is information
     */
    public boolean isWarningOrError() {
        return severity.equals("warning") || severity.equals("error");
    }

    /**
     * Whether the diagnostic meets a {@code --fail-on} threshold.
     *
     * @param failOn {@code none}, {@code warning} or {@code error}
     * @return whether it fails the command at that threshold
     */
    public boolean meets(String failOn) {
        return switch (failOn) {
            case "warning" -> isWarningOrError();
            case "error" -> severity.equals("error");
            default -> false;
        };
    }

    /** The CLI's own text format: {@code path:line:col: severity code message}. */
    @Override
    public String toString() {
        String where = line > 0 ? path + ":" + line + ":" + column : path;
        return (where + ": " + severity + " " + code + " " + message).strip();
    }
}
