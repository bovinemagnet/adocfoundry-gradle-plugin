package io.github.bovinemagnet.adocfoundry.tasks;

import java.io.File;
import java.util.List;
import java.util.Optional;
import org.gradle.api.GradleException;

/** Maps the CLI's exit codes to the task's result (spec §8). */
public final class ExitCodes {

    /** How many diagnostics a failure message lists before counting the rest. */
    static final int LISTED = 20;

    private ExitCodes() {}

    /**
     * The failure for a run, if it failed.
     *
     * @param invocation which task ran which command
     * @param result what the CLI gave back
     * @param diagnostics the problems to list for exit 1; when empty, standard error is shown
     * @param report the full report, or {@code null} when there is none
     * @param advice what to do about an exit 1
     * @return the exception to throw, or empty for exit 0
     */
    public static Optional<GradleException> failure(
            Invocation invocation, CliResult result, List<String> diagnostics, File report, String advice) {
        String task = "Task " + invocation.taskPath();
        String command = "adocfoundry-cli " + invocation.command();
        switch (result.exitCode()) {
            case 0:
                return Optional.empty();
            case 1: {
                StringBuilder message = new StringBuilder(task).append(" failed: ").append(command)
                        .append(" exited with 1.");
                if (diagnostics.isEmpty()) {
                    appendBlock(message, result.stderr());
                } else {
                    for (String diagnostic : diagnostics.subList(0, Math.min(LISTED, diagnostics.size()))) {
                        message.append("\n  ").append(diagnostic);
                    }
                    if (diagnostics.size() > LISTED) {
                        message.append("\n  and ").append(diagnostics.size() - LISTED).append(" more");
                    }
                }
                if (report != null) {
                    message.append("\nFull report: ").append(report);
                }
                if (!advice.isEmpty()) {
                    message.append('\n').append(advice);
                }
                return Optional.of(new GradleException(message.toString()));
            }
            case 2: {
                StringBuilder message = new StringBuilder(task).append(" failed: ").append(command)
                        .append(" could not run the request (exit 2). Check the adocfoundry settings this task uses"
                                + " and that workspaceDir is the folder opened in AdocFoundry.");
                appendBlock(message, result.stderr());
                return Optional.of(new GradleException(message.toString()));
            }
            default: {
                String hint = invocation.platform().cannotStartHint();
                StringBuilder message = new StringBuilder(task).append(" failed: ").append(command).append(" (")
                        .append(invocation.executable()).append(", on ").append(invocation.platform())
                        .append(") exited with ").append(result.exitCode()).append('.');
                if (!hint.isEmpty()) {
                    message.append(' ').append(hint);
                }
                appendBlock(message, result.stderr());
                return Optional.of(new GradleException(message.toString()));
            }
        }
    }

    private static void appendBlock(StringBuilder message, String text) {
        String trimmed = text.strip();
        if (!trimmed.isEmpty()) {
            message.append('\n').append(trimmed);
        }
    }
}
