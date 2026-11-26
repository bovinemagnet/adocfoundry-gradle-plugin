package io.github.bovinemagnet.adocfoundry;

import org.gradle.api.provider.Property;

/**
 * The {@code adocfoundry.audit} block, for {@code adocfoundryAudit}. Each option left unset is
 * not passed, so the workspace's {@code [quality]} table decides.
 */
public abstract class AuditSpec {

    /**
     * A component, {@code <name>}, or one version, {@code <version>@<name>}. Unset, the whole
     * workspace is audited.
     *
     * @return the component
     */
    public abstract Property<String> getComponent();

    /**
     * The least severity that fails the audit: {@code none}, {@code warning} or {@code error}.
     *
     * @return the threshold
     */
    public abstract Property<String> getFailOn();

    /**
     * Whether a partial audit fails. Only {@code true} is passed to the CLI, which has no option
     * to turn {@code [quality] require_complete} off.
     *
     * @return whether completeness is required
     */
    public abstract Property<Boolean> getRequireComplete();
}
