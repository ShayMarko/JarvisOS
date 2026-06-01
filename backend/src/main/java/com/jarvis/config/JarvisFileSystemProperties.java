package com.jarvis.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds the {@code jarvis.filesystem} block of application.yml.
 * Mirrors Appendix B of the specification: a virtual Jarvis Explorer root,
 * an allow-list of external folders, and a set of always-blocked paths.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.filesystem")
public class JarvisFileSystemProperties {

    /** Permission level for an allowed folder. */
    public enum Permission { READ_ONLY, READ_WRITE }

    /** A real folder Jarvis is permitted to access, with a permission level. */
    @Getter
    @Setter
    public static class AllowedFolder {
        private String name;
        private String path;
        private Permission permission = Permission.READ_ONLY;
    }

    /** The virtual Jarvis Explorer root — sourced from {@code jarvis.filesystem.jarvis-root}. */
    private String jarvisRoot;
    private List<String> explorerFolders = new ArrayList<>();
    private List<AllowedFolder> allowedFolders = new ArrayList<>();
    private List<String> blockedPaths = new ArrayList<>();
}
