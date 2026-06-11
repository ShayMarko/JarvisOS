package com.jarvis.connectors;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jarvis.secrets.SecretsVaultService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Injects connector credentials declared in {@code application.yml} ({@code jarvis.connector-secrets.tokens})
 * into the encrypted Secrets Vault on startup — the supported way to configure connector tokens from the
 * backend instead of typing them in the client.
 *
 * <p>Only seeds a name that isn't already in the vault, so a secret you manage by hand is never clobbered.
 * Blank values are skipped, so the feature is inert until you actually set the env vars.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectorSecretSeeder {

    private final JarvisConnectorSecretsProperties properties;
    private final SecretsVaultService vault;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        int seeded = 0;
        for (Map.Entry<String, String> e : properties.getTokens().entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (name == null || name.isBlank() || value == null || value.isBlank()) continue;
            if (vault.has(name)) continue;
            vault.store(name, "config", value, List.of());
            seeded++;
        }
        if (seeded > 0) {
            log.info("Seeded {} connector secret(s) from application.yml into the vault.", seeded);
        }
    }
}
