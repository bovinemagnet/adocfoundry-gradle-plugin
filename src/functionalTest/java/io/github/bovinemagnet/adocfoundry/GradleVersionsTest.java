package io.github.bovinemagnet.adocfoundry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** A functional test run once on each Gradle version in {@link TestProject#gradleVersions()}. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest(name = "{displayName} on Gradle {0}", quoteTextArguments = false)
@MethodSource("io.github.bovinemagnet.adocfoundry.TestProject#gradleVersions")
@interface GradleVersionsTest {}
