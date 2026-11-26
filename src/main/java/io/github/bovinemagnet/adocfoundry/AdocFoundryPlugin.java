package io.github.bovinemagnet.adocfoundry;

import io.github.bovinemagnet.adocfoundry.tasks.AdocFoundryTask;
import io.github.bovinemagnet.adocfoundry.tasks.AuditTask;
import io.github.bovinemagnet.adocfoundry.tasks.CheckTask;
import io.github.bovinemagnet.adocfoundry.tasks.DocsTreeExclusions;
import io.github.bovinemagnet.adocfoundry.tool.AdocFoundryToolService;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

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

        extension.getCheck().getFailOn().convention("error");

        Gradle gradle = project.getGradle();
        gradle.getSharedServices().registerIfAbsent(AdocFoundryToolService.NAME, AdocFoundryToolService.class, spec -> {
            spec.getParameters().getGradleUserHome().set(gradle.getGradleUserHomeDir());
            spec.getParameters().getOffline().set(gradle.getStartParameter().isOffline());
        });

        project.getTasks().withType(AdocFoundryTask.class).configureEach(task -> configure(project, extension, task));

        TaskProvider<CheckTask> check = project.getTasks().register("adocfoundryCheck", CheckTask.class, task -> {
            task.setDescription("Checks the documentation with adocfoundry-cli check.");
            task.getFailOn().convention(extension.getCheck().getFailOn());
            task.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/adocfoundry/check.json"));
        });
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task -> task.dependsOn(check));

        project.getTasks().register("adocfoundryAudit", AuditTask.class, task -> {
            task.setDescription("Audits the documentation's quality with adocfoundry-cli audit, writing JSON and SARIF.");
            task.getComponent().convention(extension.getAudit().getComponent());
            task.getFailOn().convention(extension.getAudit().getFailOn());
            task.getRequireComplete().convention(extension.getAudit().getRequireComplete());
            task.getJsonReport().convention(project.getLayout().getBuildDirectory().file("reports/adocfoundry/audit.json"));
            task.getSarifReport().convention(project.getLayout().getBuildDirectory().file("reports/adocfoundry/audit.sarif"));
        });
    }

    private static void configure(Project project, AdocFoundryExtension extension, AdocFoundryTask task) {
        task.setGroup(AdocFoundryTask.GROUP);
        task.getToolVersion().convention(extension.getToolVersion());
        task.getBaseUrl().convention(extension.getBaseUrl());
        task.getExecutable().convention(extension.getExecutable());
        task.getWorkspaceDir().convention(extension.getWorkspaceDir());
        task.getProjectDir().convention(project.getLayout().getProjectDirectory());
        ConfigurableFileTree docs = project.getObjects().fileTree().from(extension.getWorkspaceDir());
        docs.exclude(new DocsTreeExclusions());
        task.getDocsTree().from(docs);
    }
}
