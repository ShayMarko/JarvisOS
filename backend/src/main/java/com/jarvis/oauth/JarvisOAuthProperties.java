package com.jarvis.oauth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.oauth} — OAuth 2.0 providers (Google, etc.) Jarvis can run
 * the authorization-code flow against. Empty by default; add a provider's client
 * id/secret to enable it. Tokens (incl. refresh tokens) are stored encrypted in
 * the Secrets Vault, never here.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.oauth")
public class JarvisOAuthProperties {

    /** Base URL Jarvis is reachable at, used to build the redirect URI. */
    private String baseUrl = "http://localhost:8088";

    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Provider {
        private String authUrl;
        private String tokenUrl;
        private String clientId;
        private String clientSecret;
        private List<String> scopes = new ArrayList<>();
        /** Optional override; defaults to {baseUrl}/api/oauth/{provider}/callback. */
        private String redirectUri;
    }
}
