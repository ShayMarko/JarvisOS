package com.jarvis.security;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.jarvis.error.Exceptions;

/**
 * Enforces the declarative {@link JarvisPolicyProperties} restrictions. This is
 * the hard floor beneath the Approval Center: a {@code deniedCommands} match is
 * NEVER run, even if the user would otherwise approve it. Compiled eagerly in the
 * constructor so it is usable both as a Spring bean and in plain unit tests.
 */
@Component
public class RestrictionPolicy {

    /** A compiled risk rule exposed to {@link RiskClassifier}. */
    public record RiskRule(Pattern pattern, RiskLevel level) {}

    private final List<Pattern> deniedCommands;
    private final List<String> deniedHosts;
    private final List<RiskRule> riskRules;

    public RestrictionPolicy(JarvisPolicyProperties props) {
        this.deniedCommands = props.getDeniedCommands().stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                .toList();
        this.deniedHosts = props.getDeniedHosts().stream()
                .map(String::toLowerCase)
                .toList();
        this.riskRules = props.getRiskRules().stream()
                .filter(r -> r.getPattern() != null && !r.getPattern().isBlank())
                .map(r -> new RiskRule(Pattern.compile(r.getPattern(), Pattern.CASE_INSENSITIVE), r.getLevel()))
                .toList();
    }

    /** Returns a denial reason if the command matches a hard-deny rule, else empty. */
    public Optional<String> denyReasonForCommand(String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        return deniedCommands.stream()
                .filter(p -> p.matcher(command).find())
                .map(p -> "Command is denied by policy (matched /" + p.pattern() + "/)")
                .findFirst();
    }

    /** Throws {@link Exceptions.PolicyDeniedException} when a command is hard-denied. */
    public void assertCommandAllowed(String command) {
        denyReasonForCommand(command).ifPresent(reason -> {
            throw new Exceptions.PolicyDeniedException(reason);
        });
    }

    /** Returns a denial reason if the URL's host matches a denied substring, else empty. */
    public Optional<String> denyReasonForHost(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String lower = url.toLowerCase();
        return deniedHosts.stream()
                .filter(h -> !h.isBlank() && lower.contains(h))
                .map(h -> "Host is denied by policy: " + h)
                .findFirst();
    }

    /** Extra risk rules to merge on top of the built-in classifier rules. */
    public List<RiskRule> riskRules() {
        return riskRules;
    }
}
