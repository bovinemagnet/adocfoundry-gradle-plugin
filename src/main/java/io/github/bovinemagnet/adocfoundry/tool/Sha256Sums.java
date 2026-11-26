package io.github.bovinemagnet.adocfoundry.tool;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.gradle.api.GradleException;

/** A release's {@code SHA256SUMS}, in {@code sha256sum} format: {@code <hex>  <file>} per line. */
public final class Sha256Sums {

    private final Map<String, String> hashes;

    private Sha256Sums(Map<String, String> hashes) {
        this.hashes = hashes;
    }

    /**
     * Reads the file's text. Blank lines and lines that are not a hash and a name are skipped;
     * CRLF endings and {@code *} binary marks are accepted.
     *
     * @param text the file's content
     * @return the hashes by file name
     */
    public static Sha256Sums parse(String text) {
        Map<String, String> hashes = new HashMap<>();
        for (String line : text.split("\r?\n")) {
            String trimmed = line.strip();
            int space = trimmed.indexOf(' ');
            if (space != 64) {
                continue;
            }
            String hash = trimmed.substring(0, space).toLowerCase(Locale.ROOT);
            String name = trimmed.substring(space).strip();
            if (name.startsWith("*")) {
                name = name.substring(1);
            }
            if (hash.chars().allMatch(c -> Character.digit(c, 16) >= 0) && !name.isEmpty()) {
                hashes.put(name, hash);
            }
        }
        return new Sha256Sums(hashes);
    }

    /**
     * The expected hash of a file.
     *
     * @param file the file name
     * @return the lower-case hex hash, if the file is listed
     */
    public Optional<String> hashOf(String file) {
        return Optional.ofNullable(hashes.get(file));
    }

    /**
     * The expected hash of a file, which must be listed.
     *
     * @param file the file name
     * @return the lower-case hex hash
     * @throws GradleException when the file is not listed
     */
    public String requireEntry(String file) {
        return hashOf(file).orElseThrow(() -> new GradleException("SHA256SUMS has no line for " + file
                + ", so it cannot be verified. Check adocfoundry.toolVersion and adocfoundry.baseUrl,"
                + " or set adocfoundry.executable."));
    }

    /**
     * Checks a file's actual hash against its line.
     *
     * @param file the file name
     * @param actual the file's hex SHA-256
     * @throws GradleException when the file is not listed or the hashes differ
     */
    public void requireMatch(String file, String actual) {
        String expected = requireEntry(file);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new GradleException("The download of " + file + " does not match SHA256SUMS: expected "
                    + expected + ", got " + actual.toLowerCase(Locale.ROOT)
                    + ". It was deleted. Try again, and check adocfoundry.baseUrl if it happens again.");
        }
    }
}
