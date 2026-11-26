package io.github.bovinemagnet.adocfoundry;

import io.github.bovinemagnet.adocfoundry.tool.AdocFoundryToolService;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.BasePlugin;

/**
 * The {@code io.github.bovinemagnet.adocfoundry} plugin, which runs {@code adocfoundry-cli} to
 * check documentation and export PDFs.
 */
public abstract class AdocFoundryPlugin implements Plugin<Project> {

    /** The CLI version this plugin release is tested with, used unless a build sets another. */
    public static final String DEFAULT_TOOL_VERSION = "0.15.0";

    /** Where AdocFoundry's releases are published. */
    public static final String DEFAULT_BASE_URL =
            "https://github.com/bovinemagnet/AdocFoundry/releases/download";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);

        AdocFoundryExtension extension =
                project.getExtensions().create(AdocFoundryExtension.NAME, AdocFoundryExtension.class);
        extension.getToolVersion().convention(DEFAULT_TOOL_VERSION);
        extension.getBaseUrl().convention(DEFAULT_BASE_URL);
        extension.getWorkspaceDir().convention(project.getLayout().getProjectDirectory());
        extension.getWarnAboutUserSettings().convention(true);

        Gradle gradle = project.getGradle();
        gradle.getSharedServices().registerIfAbsent(AdocFoundryToolService.NAME, AdocFoundryToolService.class, spec -> {
            spec.getParameters().getGradleUserHome().set(gradle.getGradleUserHomeDir());
            spec.getParameters().getOffline().set(gradle.getStartParameter().isOffline());
        });
    }
}
