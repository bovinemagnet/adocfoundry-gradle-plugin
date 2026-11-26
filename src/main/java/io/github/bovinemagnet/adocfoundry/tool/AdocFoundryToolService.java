package io.github.bovinemagnet.adocfoundry.tool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.process.ExecOperations;

/**
 * Resolves {@code adocfoundry-cli} once per build, for every task that runs it.
 *
 * <p>Registered by the plugin as {@value #NAME}; tasks reach it with {@code @ServiceReference}.
 */
public abstract class AdocFoundryToolService implements BuildService<AdocFoundryToolService.Params> {

    /** The name the service is registered under. */
    public static final String NAME = "adocfoundryTool";

    /** The service's parameters. */
    public interface Params extends BuildServiceParameters {
        /**
         * @return the Gradle user home, which holds the download cache
         */
        DirectoryProperty getGradleUserHome();

        /**
         * @return whether the build runs with {@code --offline}
         */
        Property<Boolean> getOffline();
    }

    private final Map<ToolRequest, Path> resolved = new HashMap<>();

    /**
     * @return Gradle's archive service
     */
    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    /**
     * @return Gradle's file service
     */
    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    /**
     * @return Gradle's process service
     */
    @Inject
    protected abstract ExecOperations getExecOperations();

    /**
     * The binary to run for this platform, downloading and verifying it when it is not cached.
     *
     * @param version {@code adocfoundry.toolVersion}
     * @param baseUrl {@code adocfoundry.baseUrl}
     * @param executable {@code adocfoundry.executable}, or {@code null}
     * @return the binary
     */
    public synchronized Path resolve(String version, String baseUrl, File executable) {
        ToolRequest request = new ToolRequest(
                version, baseUrl, executable == null ? null : executable.toPath(), Platform.current());
        Path binary = resolved.get(request);
        if (binary == null) {
            ToolResolver resolver = new ToolResolver(
                    getParameters().getGradleUserHome().get().getAsFile().toPath(),
                    getParameters().getOffline().get(),
                    new GradleArchiveExtractor(getArchiveOperations(), getFileSystemOperations()),
                    this::versionOutput);
            binary = resolver.resolve(request);
            resolved.put(request, binary);
        }
        return binary;
    }

    private String versionOutput(Path binary) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            getExecOperations().exec(spec -> {
                spec.setExecutable(binary.toString());
                spec.args("--version");
                spec.setStandardOutput(out);
                spec.setErrorOutput(new ByteArrayOutputStream());
            });
        } catch (RuntimeException e) {
            throw cannotStart(binary, e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * The failure for a binary that could not be started or ran badly.
     *
     * @param binary the binary
     * @param cause what went wrong
     * @return the exception to throw
     */
    public static GradleException cannotStart(Path binary, Throwable cause) {
        Platform platform = Platform.current();
        String hint = platform.cannotStartHint();
        return new GradleException("Could not run " + binary + " on " + platform + ": " + cause.getMessage()
                + (hint.isEmpty() ? "" : ". " + hint), cause);
    }
}
