package com.jarvis.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.jarvis.config.JarvisSecurityProperties;

class VaultCryptoTest {

    private static JarvisSecurityProperties props() {
        JarvisSecurityProperties p = new JarvisSecurityProperties();
        p.setVaultKey("test-vault-key");
        return p;
    }

    private final VaultCrypto crypto = new VaultCrypto(props());

    @Test
    void roundTripsValue() {
        String secret = "ghp_supersecrettoken1234";
        String encrypted = crypto.encrypt(secret);
        assertThat(encrypted).doesNotContain(secret); // ciphertext must not leak plaintext
        assertThat(crypto.decrypt(encrypted)).isEqualTo(secret);
    }

    @Test
    void usesFreshIvPerEncryption() {
        // Same plaintext encrypts to different ciphertext (random IV).
        assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
    }
}
