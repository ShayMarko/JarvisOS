package com.jarvis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.jarvis.security.PermissionMode;

import lombok.Getter;
import lombok.Setter;

/** Binds {@code jarvis.security} — permission mode + the Secrets Vault key. */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.security")
public class JarvisSecurityProperties {

    private PermissionMode permissionMode = PermissionMode.ASSISTED;

    /** Passphrase used to derive the Secrets Vault key — sourced from {@code jarvis.security.vault-key}. */
    private String vaultKey;
}
