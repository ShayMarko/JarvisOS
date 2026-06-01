package com.jarvis.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.policy} — a declarative, user-editable restrictions policy
 * (spec §10 "safety/restrictions"). Unlike the hard-coded {@link RiskClassifier}
 * rules, these live in {@code application.yml} so the user can tighten or relax
 * Jarvis without recompiling:
 *
 * <ul>
 *   <li>{@code deniedCommands} — regex patterns that are NEVER run, even with approval.</li>
 *   <li>{@code deniedHosts} — host substrings that web tools must refuse.</li>
 *   <li>{@code riskRules} — extra (pattern → risk level) rules merged into the classifier.</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.policy")
public class JarvisPolicyProperties {

    /** Regex patterns (case-insensitive) for commands that are hard-denied. */
    private List<String> deniedCommands = new ArrayList<>();

    /** Host substrings web fetches/searches must refuse (e.g. an internal admin host). */
    private List<String> deniedHosts = new ArrayList<>();

    /** Extra risk-classification rules, applied on top of the built-in set. */
    private List<RiskRule> riskRules = new ArrayList<>();

    /** A configurable risk rule: a regex and the risk level it confers. */
    @Getter
    @Setter
    public static class RiskRule {
        private String pattern;
        private RiskLevel level = RiskLevel.MEDIUM;
    }
}
