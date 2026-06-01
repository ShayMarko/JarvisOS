package com.jarvis.secrets;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An encrypted credential in the Secrets Vault (spec §11.3). The plaintext is
 * NEVER stored or exposed; only the AES-GCM ciphertext and a short hint (last
 * few chars) for display are persisted.
 */
@Entity
@Table(name = "secret")
public class Secret {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String connector;

    @Column(name = "encrypted_value", nullable = false, length = 4000)
    private String encryptedValue;

    /** Last few characters, for masked display (e.g. show "••••1234"). */
    private String hint;

    /** Comma-separated permission scopes for this credential. */
    private String scopes;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    protected Secret() {
        // for JPA
    }

    public Secret(String id, String name, String connector, String encryptedValue,
                  String hint, String scopes) {
        this.id = id;
        this.name = name;
        this.connector = connector;
        this.encryptedValue = encryptedValue;
        this.hint = hint;
        this.scopes = scopes;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getConnector() { return connector; }
    public String getEncryptedValue() { return encryptedValue; }
    public String getHint() { return hint; }
    public String getScopes() { return scopes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
}
