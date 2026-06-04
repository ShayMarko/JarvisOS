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

    /**
     * Optional API token. When set, every {@code /api/**} request must carry it (header
     * {@code X-Jarvis-Token}, {@code Authorization: Bearer <token>}, or {@code ?token=}). Blank = open
     * (the default; the server already binds to loopback). Set this before exposing Jarvis off-box.
     */
    private String apiToken = "";
}
