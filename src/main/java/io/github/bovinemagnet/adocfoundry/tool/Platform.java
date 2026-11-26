package io.github.bovinemagnet.adocfoundry.tool;

import java.util.Locale;
import org.gradle.api.GradleException;

/**
 * An operating system and processor that AdocFoundry publishes {@code adocfoundry-cli} for.
 *
 * @param os the operating system
 * @param arch the processor, as AdocFoundry names it in release assets
 */
public record Platform(Os os, String arch) {

    /** The operating systems with a release. */
    public enum Os {
        /** Linux, built against glibc. */
        LINUX,
        /** macOS, as one universal binary. */
        MACOS,
        /** Windows. */
        WINDOWS
    }

    /**
     * The platform this JVM runs on.
     *
     * @return the platform
     */
    public static Platform current() {
        return of(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    /**
     * The platform named by Java's {@code os.name} and {@code os.arch}.
     *
     * @param osName the value of {@code os.name}
     * @param osArch the value of {@code os.arch}
     * @return the platform
     * @throws GradleException when AdocFoundry publishes no binary for it
     */
    public static Platform of(String osName, String osArch) {
        String name = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        if (name.startsWith("linux") && (x64 || arm64)) {
            return new Platform(Os.LINUX, x64 ? "x86_64" : "aarch64");
        }
        if ((name.startsWith("mac") || name.startsWith("darwin")) && (x64 || arm64)) {
            return new Platform(Os.MACOS, "universal");
        }
        if (name.startsWith("windows") && (x64 || arm64)) {
            return new Platform(Os.WINDOWS, x64 ? "x64" : "arm64");
        }
        throw new GradleException("AdocFoundry publishes no adocfoundry-cli for " + osName + " " + osArch
                + ". Build it from https://github.com/bovinemagnet/AdocFoundry and set adocfoundry.executable to it.");
    }

    /**
     * The platform's part of an asset name and of the cache path, such as {@code linux-x86_64}.
     *
     * @return the identifier
     */
    public String id() {
        return os.name().toLowerCase(Locale.ROOT) + "-" + arch;
    }

    /**
     * The binary's file name inside the archive.
     *
     * @return {@code adocfoundry-cli}, or {@code adocfoundry-cli.exe} on Windows
     */
    public String executableName() {
        return os == Os.WINDOWS ? "adocfoundry-cli.exe" : "adocfoundry-cli";
    }

    /**
     * What to add to a message when the binary cannot start on this platform.
     *
     * @return the hint, or an empty string when there is none
     */
    public String cannotStartHint() {
        if (os != Os.LINUX) {
            return "";
        }
        return "The Linux adocfoundry-cli needs glibc 2.35 or later (Ubuntu 22.04+, Debian 12+); it does not run"
                + " on RHEL 8 or 9, Amazon Linux 2 or Alpine, where it usually fails with \"No such file or"
                + " directory\". Set adocfoundry.executable to a binary built for this system.";
    }

    @Override
    public String toString() {
        return id();
    }
}
