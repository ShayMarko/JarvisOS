package com.jarvis.secrets;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import com.jarvis.common.Ids;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jarvis.audit.AuditService;
import com.jarvis.config.JarvisLimitsProperties;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * The Secrets Vault / Credentials Manager (spec §11.3). Stores credentials
 * encrypted, exposes only masked metadata, and is the ONLY path to a plaintext
 * value — which is reserved for internal connector adapters and is never
 * returned over the API or placed in any LLM context. Every access is audited.
 */
@Service
@RequiredArgsConstructor
public class SecretsVaultService {

    private final SecretRepository repository;
    private final VaultCrypto crypto;
    private final AuditService audit;
    private final JarvisLimitsProperties limits;

    @Transactional
    public SecretView store(String name, String connector, String value, List<String> scopes) {
        int hintLen = limits.getSecretHintLength();
        String hint = value.length() <= hintLen ? "" : value.substring(value.length() - hintLen);
        Secret secret = new Secret(
                Ids.generate("sec"),
                name, connector, crypto.encrypt(value), hint,
                scopes == null ? "" : String.join(",", scopes));
        Secret saved = repository.save(secret);
        audit.record("SECRET", "secret:create", name, "OK", "id=" + saved.getId());
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<SecretView> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    /** Whether a secret with this name exists (no decryption, no audit). */
    @Transactional(readOnly = true)
    public boolean has(String name) {
        return repository.findAllByOrderByNameAsc().stream().anyMatch(s -> s.getName().equals(name));
    }

    /**
     * Decrypt a secret by its name — INTERNAL USE ONLY (connector adapters).
     * Returns null if absent. Access is audited via {@link #reveal(String)}.
     */
    @Transactional
    public String revealByName(String name) {
        return repository.findAllByOrderByNameAsc().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .map(s -> reveal(s.getId()))
                .orElse(null);
    }

    /**
     * Returns the decrypted value — INTERNAL USE ONLY (connector adapters).
     * Not exposed by any controller. Access is audited.
     */
    @Transactional
    public String reveal(String id) {
        Secret secret = require(id);
        secret.setLastAccessedAt(Instant.now());
        repository.save(secret);
        audit.record("SECRET", "secret:access", secret.getName(), "OK", "id=" + id);
        return crypto.decrypt(secret.getEncryptedValue());
    }

    @Transactional
    public void revoke(String id) {
        Secret secret = require(id);
        repository.delete(secret);
        audit.record("SECRET", "secret:revoke", secret.getName(), "OK", "id=" + id);
    }

    private Secret require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No secret " + id));
    }

    private SecretView toView(Secret s) {
        List<String> scopes = (s.getScopes() == null || s.getScopes().isBlank())
                ? List.of()
                : Arrays.asList(s.getScopes().split(","));
        String masked = "••••" + (s.getHint() == null ? "" : s.getHint());
        return new SecretView(s.getId(), s.getName(), s.getConnector(), scopes, masked,
                s.getCreatedAt(), s.getLastAccessedAt());
    }
}
