package com.jarvis.project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.projects} — Project Intelligence settings (spec §6.4 of the
 * old PRD). Jarvis scans a few developer roots, detects each project's type by its
 * marker file, and opens it in the right IDE.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.projects")
public class JarvisProjectsProperties {

    /** Folders to scan for projects. {@code ~} expands to the user's home. */
    private List<String> scanRoots = List.of("~/Projects", "~/Developer", "~/Code");

    /** How deep below each scan root to look for a project marker. */
    private int maxDepth = 2;

    /** App to open when no type-specific IDE is configured. */
    private String defaultIde = "Visual Studio Code";

    /** Maps a detected project type to the macOS application that should open it. */
    private Map<String, String> ideByType = defaultIdeMap();

    private static Map<String, String> defaultIdeMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("maven", "IntelliJ IDEA");
        m.put("gradle", "IntelliJ IDEA");
        m.put("node", "Visual Studio Code");
        m.put("python", "Visual Studio Code");
        m.put("rust", "Visual Studio Code");
        m.put("go", "Visual Studio Code");
        m.put("xcode", "Xcode");
        return m;
    }
}
