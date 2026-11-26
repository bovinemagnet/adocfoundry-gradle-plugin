package io.github.bovinemagnet.adocfoundry;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * The {@code adocfoundry} extension: which {@code adocfoundry-cli} to run, and over which
 * workspace.
 */
public abstract class AdocFoundryExtension {

    /** The name the extension is registered under. */
    public static final String NAME = "adocfoundry";

    /**
     * The CLI version to download. Defaults to the version this plugin release was tested with.
     *
     * @return the version, such as {@code 0.15.0}
     */
    public abstract Property<String> getToolVersion();

    /**
     * A local {@code adocfoundry-cli} to run in place of a download. Nothing is downloaded when
     * it is set.
     *
     * @return the executable
     */
    public abstract RegularFileProperty getExecutable();

    /**
     * Where releases are downloaded from: {@code <baseUrl>/v<version>/<asset>}. Defaults to
     * AdocFoundry's GitHub releases; set it to use a mirror.
     *
     * @return the base URL
     */
    public abstract Property<String> getBaseUrl();

    /**
     * The folder opened in AdocFoundry, whose {@code .adocfoundry.toml} and playbook apply.
     * Defaults to the project directory.
     *
     * @return the workspace root
     */
    public abstract DirectoryProperty getWorkspaceDir();

    /**
     * Whether to warn when AdocFoundry's user settings file sets PDF options, which the build
     * does not read. Defaults to {@code true}.
     *
     * @return whether to warn
     */
    public abstract Property<Boolean> getWarnAboutUserSettings();

    /**
     * Settings for {@code adocfoundryCheck}.
     *
     * @return the check settings
     */
    @Nested
    public abstract CheckSpec getCheck();

    /**
     * Configures {@code adocfoundryCheck}.
     *
     * @param action the configuration
     */
    public void check(Action<? super CheckSpec> action) {
        action.execute(getCheck());
    }
}
