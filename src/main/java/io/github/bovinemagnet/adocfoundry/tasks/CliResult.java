package io.github.bovinemagnet.adocfoundry.tasks;

/**
 * What a run of the CLI gave back.
 *
 * @param exitCode the exit code
 * @param stdout standard output
 * @param stderr standard error
 */
public record CliResult(int exitCode, String stdout, String stderr) {}
