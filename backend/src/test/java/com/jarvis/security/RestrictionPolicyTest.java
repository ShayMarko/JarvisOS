package com.jarvis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jarvis.error.Exceptions.PolicyDeniedException;

class RestrictionPolicyTest {

    private RestrictionPolicy policyWith(List<String> deniedCommands, List<String> deniedHosts) {
        JarvisPolicyProperties props = new JarvisPolicyProperties();
        props.setDeniedCommands(deniedCommands);
        props.setDeniedHosts(deniedHosts);
        return new RestrictionPolicy(props);
    }

    @Test
    void allowsCommandsThatMatchNoDenyRule() {
        RestrictionPolicy policy = policyWith(List.of("\\brm\\s+-rf?\\s+/(\\s|$)"), List.of());
        assertThat(policy.denyReasonForCommand("ls -la")).isEmpty();
        policy.assertCommandAllowed("echo hello"); // does not throw
    }

    @Test
    void deniesCommandsThatMatchARule() {
        RestrictionPolicy policy = policyWith(List.of("\\brm\\s+-rf?\\s+/(\\s|$)"), List.of());
        assertThat(policy.denyReasonForCommand("rm -rf /")).isPresent();
        assertThatThrownBy(() -> policy.assertCommandAllowed("rm -rf /"))
                .isInstanceOf(PolicyDeniedException.class);
    }

    @Test
    void deniesHostsBySubstringCaseInsensitively() {
        RestrictionPolicy policy = policyWith(List.of(), List.of("internal-admin.local"));
        assertThat(policy.denyReasonForHost("https://INTERNAL-admin.local/x")).isPresent();
        assertThat(policy.denyReasonForHost("https://example.com")).isEmpty();
    }

    @Test
    void exposesConfiguredRiskRules() {
        JarvisPolicyProperties props = new JarvisPolicyProperties();
        JarvisPolicyProperties.RiskRule rule = new JarvisPolicyProperties.RiskRule();
        rule.setPattern("\\bgit\\s+push\\s+--force\\b");
        rule.setLevel(RiskLevel.HIGH);
        props.setRiskRules(List.of(rule));

        RestrictionPolicy policy = new RestrictionPolicy(props);
        assertThat(policy.riskRules()).singleElement()
                .satisfies(r -> assertThat(r.level()).isEqualTo(RiskLevel.HIGH));
    }
}
