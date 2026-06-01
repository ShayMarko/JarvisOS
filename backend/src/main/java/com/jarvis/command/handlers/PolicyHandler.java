package com.jarvis.command.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.config.JarvisSecurityProperties;
import com.jarvis.security.JarvisPolicyProperties;

import lombok.RequiredArgsConstructor;

/** {@code /policy} — shows the active restrictions policy and permission mode. */
@Component
@RequiredArgsConstructor
public class PolicyHandler implements CommandHandler {

    private final JarvisPolicyProperties policy;
    private final JarvisSecurityProperties security;

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("policy", "/policy",
                "Show the active safety restrictions (denied commands/hosts, risk rules).",
                CommandCategory.SECURITY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("permissionMode", security.getPermissionMode());
        data.put("deniedCommands", policy.getDeniedCommands());
        data.put("deniedHosts", policy.getDeniedHosts());
        data.put("riskRules", policy.getRiskRules().stream()
                .map(r -> r.getPattern() + " → " + r.getLevel())
                .toList());
        return CommandResult.ok("policy", "Active safety policy", data);
    }
}
