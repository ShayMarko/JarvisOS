package com.jarvis.security;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Classifies the risk of a terminal command by pattern matching (spec §18.1
 * "terminal risk classification"). This is intentionally conservative — when in
 * doubt it rates higher, because the result only decides how much scrutiny the
 * Approval Center applies, never whether to silently run something.
 */
@Component
public class RiskClassifier {

    private record Rule(Pattern pattern, RiskLevel level) {}

    private static final List<Rule> RULES = List.of(
            // Catastrophic / irreversible
            rule("\\brm\\s+-rf?\\b", RiskLevel.CRITICAL),
            rule("\\bmkfs\\b|\\bdd\\b\\s+if=|>\\s*/dev/[a-z]", RiskLevel.CRITICAL),
            rule(":\\(\\)\\s*\\{.*\\};", RiskLevel.CRITICAL),          // fork bomb
            rule("\\bshutdown\\b|\\breboot\\b|\\bhalt\\b", RiskLevel.CRITICAL),
            // High: privilege, remote code, destructive infra
            rule("\\bsudo\\b|\\bsu\\b", RiskLevel.HIGH),
            rule("curl[^|]*\\|\\s*(sh|bash)|wget[^|]*\\|\\s*(sh|bash)", RiskLevel.HIGH),
            rule("\\bchmod\\s+-?R?\\s*777\\b|\\bchown\\b", RiskLevel.HIGH),
            rule("\\bgit\\s+push\\b|\\bkubectl\\s+delete\\b|\\bterraform\\s+(apply|destroy)\\b", RiskLevel.HIGH),
            rule("\\bdocker\\s+(rm|rmi|system\\s+prune)\\b", RiskLevel.HIGH),
            // Medium: mutating, package installs, network writes
            rule("\\b(rm|mv|chmod|chown|kill|pkill)\\b", RiskLevel.MEDIUM),
            rule("\\b(npm|pip|pip3|brew|apt|apt-get|yum|gem|cargo)\\s+(install|add|i)\\b", RiskLevel.MEDIUM),
            rule("\\bgit\\s+(reset|clean|checkout)\\b", RiskLevel.MEDIUM),
            rule(">\\s*\\S|>>\\s*\\S", RiskLevel.MEDIUM)                // output redirection (writes files)
    );

    public RiskLevel classify(String command) {
        if (command == null || command.isBlank()) {
            return RiskLevel.LOW;
        }
        String c = command.toLowerCase();
        RiskLevel highest = RiskLevel.LOW;
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(c).find() && rule.level().ordinal() > highest.ordinal()) {
                highest = rule.level();
            }
        }
        return highest;
    }

    private static Rule rule(String regex, RiskLevel level) {
        return new Rule(Pattern.compile(regex), level);
    }
}
