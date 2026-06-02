package com.jarvis.plugin.spi;

import java.util.List;

import com.jarvis.ai.tools.Tool;

/**
 * The Plugin SDK contract (spec §8). An external plugin is a JAR that:
 * <ol>
 *   <li>implements this interface,</li>
 *   <li>declares the implementation in {@code META-INF/services/com.jarvis.plugin.spi.JarvisPlugin}
 *       (standard Java {@link java.util.ServiceLoader} discovery), and</li>
 *   <li>contributes one or more {@link Tool}s the agent loop can call.</li>
 * </ol>
 * Jarvis loads each JAR in its own class loader at runtime, registers the
 * contributed tools by name, and grants them to the General agent — so a dropped-in
 * JAR adds new capabilities without rebuilding Jarvis. Plugins compile against the
 * Jarvis core (the {@code Tool}/{@code ToolSpec} types are part of this SDK surface).
 */
public interface JarvisPlugin {

    /** Stable unique id, e.g. {@code "com.acme.weather"}. Used to install/uninstall. */
    String id();

    /** Human-readable name shown in the UI. */
    String name();

    /** Semantic version of the plugin build. */
    default String version() {
        return "1.0.0";
    }

    /** One-line description of what the plugin adds. */
    default String description() {
        return "";
    }

    /** The tools this plugin contributes to the agent loop. */
    List<Tool> tools();
}
