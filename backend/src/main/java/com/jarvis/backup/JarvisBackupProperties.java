package com.jarvis.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.backup}. {@code cloudDir} is a folder a cloud client syncs (Dropbox/Drive/iCloud);
 * encrypted backups are written there so an off-box copy is always ciphertext. Blank = keep them local.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.backup")
public class JarvisBackupProperties {

    private String cloudDir = "";
}
