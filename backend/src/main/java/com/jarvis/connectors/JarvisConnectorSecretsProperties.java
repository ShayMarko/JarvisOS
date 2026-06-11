package com.jarvis.connectors;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.connector-secrets.tokens} — connector credentials supplied from backend config
 * (application.yml / environment), never entered in the client. Seeded into the encrypted Secrets
 * Vault on startup by {@link ConnectorSecretSeeder}. Map keys are vault secret names (e.g.
 * {@code github-token}); blank values are ignored, so this is inert until you set the env vars.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.connector-secrets")
public class JarvisConnectorSecretsProperties {

    /** vault-secret-name → token value. Blank values are skipped. */
    private Map<String, String> tokens = new LinkedHashMap<>();
}
