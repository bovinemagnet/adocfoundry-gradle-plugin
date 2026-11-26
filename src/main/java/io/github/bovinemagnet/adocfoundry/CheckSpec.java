package io.github.bovinemagnet.adocfoundry;

import org.gradle.api.provider.Property;

/** The {@code adocfoundry.check} block, for {@code adocfoundryCheck}. */
public abstract class CheckSpec {

    /**
     * The least severity that fails the check: {@code warning} or {@code error}. Defaults to
     * {@code error}, as the CLI does. An unresolved xref is a warning.
     *
     * @return the threshold
     */
    public abstract Property<String> getFailOn();
}
