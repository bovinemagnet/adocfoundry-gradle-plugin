package io.github.bovinemagnet.adocfoundry.tasks;

import io.github.bovinemagnet.adocfoundry.tool.Platform;
import java.nio.file.Path;

/**
 * Which task ran which CLI command, for failure messages.
 *
 * @param taskPath the task's path, such as {@code :adocfoundryCheck}
 * @param command the CLI command, such as {@code check}
 * @param executable the binary that ran
 * @param platform the platform it ran on
 */
public record Invocation(String taskPath, String command, Path executable, Platform platform) {}
