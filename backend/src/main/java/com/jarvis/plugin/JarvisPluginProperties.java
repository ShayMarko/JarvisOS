package com.jarvis.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Plugin loader configuration ({@code jarvis.plugins}). Plugins are JARs dropped
 * into {@link #dir}; if {@code dir} is blank it resolves to {@code <jarvisRoot>/Plugins}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.plugins")
public class JarvisPluginProperties {

    /** Master switch. When false, no external JARs are loaded (built-in tools still work). */
    private boolean enabled = true;

    /** Folder scanned for *.jar plugins. Blank ⇒ {@code <jarvisRoot>/Plugins}. */
    private String dir = "";

    /** Optional catalog file (JSON array of available plugins). Blank ⇒ {@code <dir>/catalog.json}. */
    private String catalog = "";
}
