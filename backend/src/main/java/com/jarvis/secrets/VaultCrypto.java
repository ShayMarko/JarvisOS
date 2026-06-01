package com.jarvis.secrets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.jarvis.config.JarvisSecurityProperties;

/**
 * AES-GCM encryption for the Secrets Vault (spec §11.3 "Encrypted Tokens").
 * The key is derived from a configured passphrase; each value gets a random IV
 * and the stored form is base64(iv || ciphertext+tag).
 *
 * <p>Phase-5 grade: keeps secrets encrypted at rest and out of plaintext storage.
 * Production hardening (OS keychain / KMS, key rotation) is a later step.
 */
@Component
public class VaultCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public VaultCrypto(JarvisSecurityProperties props) {
        this.key = deriveKey(props.getVaultKey());
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ct, 0, combined, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Vault encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Vault decryption failed", e);
        }
    }

    private static SecretKeySpec deriveKey(String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException("jarvis.security.vault-key must be set");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive vault key", e);
        }
    }
}
