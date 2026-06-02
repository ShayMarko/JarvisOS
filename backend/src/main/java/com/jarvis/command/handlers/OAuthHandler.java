package com.jarvis.command.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.oauth.OAuthService;

import lombok.RequiredArgsConstructor;

/** {@code /oauth} — shows OAuth providers and how to connect them. */
@Component
@RequiredArgsConstructor
public class OAuthHandler implements CommandHandler {

    private final OAuthService oauth;

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("oauth", "/oauth",
                "Show OAuth providers and connect/authorize them.", CommandCategory.SECURITY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        var configured = oauth.configuredProviders();
        var connected = oauth.connectedProviders();
        Map<String, Object> data = new LinkedHashMap<>();
        for (String p : configured) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("status", connected.contains(p) ? "connected" : "not connected");
            if (!connected.contains(p)) {
                info.put("authorizeUrl", oauth.authorizeUrl(p, p));
            }
            data.put(p, info);
        }
        String message = configured.isEmpty()
                ? "No OAuth providers configured. Add one under jarvis.oauth.providers (client id/secret) to connect Google etc."
                : "Open a provider's authorizeUrl to grant access; tokens are stored encrypted and auto-refreshed.";
        return CommandResult.ok("oauth", message, data);
    }
}
