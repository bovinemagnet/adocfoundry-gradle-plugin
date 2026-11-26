package io.github.bovinemagnet.adocfoundry;

import io.github.bovinemagnet.adocfoundry.tasks.AdocFoundryTask;
import io.github.bovinemagnet.adocfoundry.tasks.AuditTask;
import io.github.bovinemagnet.adocfoundry.tasks.CheckTask;
import io.github.bovinemagnet.adocfoundry.tasks.DocsTreeExclusions;
import io.github.bovinemagnet.adocfoundry.tasks.PdfTask;
import io.github.bovinemagnet.adocfoundry.tool.AdocFoundryToolService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.Directory;
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

        project.getTasks().register("adocfoundryPdf", task -> {
            task.setGroup(AdocFoundryTask.GROUP);
            task.setDescription("Exports every adocfoundry.pdfs entry.");
            task.dependsOn(project.getTasks().withType(PdfTask.class));
        });
        extension.getPdfs().all(entry -> registerPdf(project, extension, entry));
    }

    private static void registerPdf(Project project, AdocFoundryExtension extension, PdfExport entry) {
        String name = entry.getName();
        entry.getFailOn().convention("none");
        entry.getOutput().convention(project.getLayout().getBuildDirectory().file("adocfoundry/pdf/" + name + ".pdf"));
        Directory projectDir = project.getLayout().getProjectDirectory();
        String taskName = "adocfoundryPdf" + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        project.getTasks().register(taskName, PdfTask.class, task -> {
            task.setDescription("Exports the PDF adocfoundry.pdfs." + name + ".");
            task.getEntryName().convention(name);
            task.getComponent().convention(entry.getComponent());
            task.getPage().convention(entry.getPage());
            task.getProfile().convention(entry.getProfile());
            task.getTheme().convention(entry.getTheme());
            task.getThemeFile().from(entry.getTheme().map(theme -> themeFiles(projectDir, theme)).orElse(new ArrayList<>()));
            task.getFontsDir().convention(entry.getFontsDir());
            task.getStandards().convention(entry.getStandards());
            task.getFailOn().convention(entry.getFailOn());
            task.getOutput().convention(entry.getOutput());
            task.getWarnAboutUserSettings().convention(extension.getWarnAboutUserSettings());
        });
    }

    /** The theme file a {@code theme} value names, if it names one, for the task's inputs. */
    private static List<File> themeFiles(Directory projectDir, String theme) {
        List<File> files = new ArrayList<>();
        if (PdfTask.isThemeFile(theme)) {
            files.add(new File(PdfTask.themeArgument(theme, projectDir.getAsFile())));
        }
        return files;
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
