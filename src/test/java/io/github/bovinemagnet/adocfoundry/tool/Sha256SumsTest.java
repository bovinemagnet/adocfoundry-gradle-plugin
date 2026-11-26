package io.github.bovinemagnet.adocfoundry.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class Sha256SumsTest {

    private static final String A = "a4dfb03dae0bb200678bb67bdf491a90218f46c2a4e1ecacee0a9e144741a859";
    private static final String B = "3ce95daca29f7d8e7b0d0e19fbca7efd279ebfcca239d2367dc7b2ae538c32b2";

    @Test
    void readsSha256sumFormat() {
        Sha256Sums sums = Sha256Sums.parse(A + "  adocfoundry-cli-0.15.0-linux-x86_64.tar.gz\n"
                + B + "  adocfoundry-cli-0.15.0-linux-aarch64.tar.gz\n");

        assertThat(sums.hashOf("adocfoundry-cli-0.15.0-linux-x86_64.tar.gz")).contains(A);
        assertThat(sums.hashOf("adocfoundry-cli-0.15.0-linux-aarch64.tar.gz")).contains(B);
    }

    @Test
    void readsCrlfBinaryMarksAndUpperCase() {
        Sha256Sums sums = Sha256Sums.parse(A.toUpperCase() + " *one.zip\r\n\r\n" + B + "  two.zip\r\n");

        assertThat(sums.hashOf("one.zip")).contains(A);
        assertThat(sums.hashOf("two.zip")).contains(B);
    }

    @Test
    void missingEntryIsEmpty() {
        Sha256Sums sums = Sha256Sums.parse(A + "  one.zip\n");

        assertThat(sums.hashOf("two.zip")).isEmpty();
        assertThatThrownBy(() -> sums.requireMatch("two.zip", A))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("SHA256SUMS has no line for two.zip");
    }

    @Test
    void wrongHashNamesBoth() {
        Sha256Sums sums = Sha256Sums.parse(A + "  one.zip\n");

        sums.requireMatch("one.zip", A);
        assertThatThrownBy(() -> sums.requireMatch("one.zip", B))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining(A)
                .hasMessageContaining(B);
    }
}
