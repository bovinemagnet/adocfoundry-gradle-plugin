package io.github.bovinemagnet.adocfoundry.tool;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;

/**
 * An {@code adocfoundry-cli} version, compared numerically. A suffix such as {@code -dev} is
 * ignored.
 *
 * @param major the major version
 * @param minor the minor version
 * @param patch the patch version
 */
public record ToolVersion(int major, int minor, int patch) implements Comparable<ToolVersion> {

    /** The oldest CLI whose behaviour the plugin relies on. */
    public static final ToolVersion MINIMUM = new ToolVersion(0, 15, 0);

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+.][0-9A-Za-z.+-]*)?");
    private static final Pattern VERSION_LINE = Pattern.compile("adocfoundry-cli\\s+(\\S+)");

    /**
     * Parses {@code x.y.z}.
     *
     * @param text the version
     * @return the version
     * @throws GradleException when the text is not a version
     */
    public static ToolVersion parse(String text) {
        Matcher matcher = VERSION.matcher(text.strip());
        if (!matcher.matches()) {
            throw new GradleException("'" + text + "' is not an adocfoundry-cli version such as "
                    + MINIMUM + ". Set adocfoundry.toolVersion to a released version.");
        }
        return new ToolVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    /**
     * Parses what {@code adocfoundry-cli --version} prints: {@code adocfoundry-cli 0.15.0}.
     *
     * @param output the command's standard output
     * @return the version
     * @throws GradleException when the output names no version
     */
    public static ToolVersion fromVersionOutput(String output) {
        Matcher matcher = VERSION_LINE.matcher(output.strip());
        if (matcher.lookingAt()) {
            try {
                return parse(matcher.group(1));
            } catch (GradleException e) {
                // Reported below, with the whole output.
            }
        }
        throw new GradleException("adocfoundry-cli --version printed '" + output.strip()
                + "', not 'adocfoundry-cli <version>'. Check that adocfoundry.executable is adocfoundry-cli.");
    }

    @Override
    public int compareTo(ToolVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
