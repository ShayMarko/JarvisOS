package com.jarvis.watchdog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.watchdog} — the self-healing heartbeat for the headless box. Periodically checks
 * the things Jarvis depends on (the local Ollama model server, free disk) and alerts the owner (via the
 * Notification Center → Discord/Telegram) when something breaks, with optional auto-restart of Ollama.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.watchdog")
public class JarvisWatchdogProperties {

    private boolean enabled = true;
    /** How often the watchdog checks (ms). */
    private long checkIntervalMs = 120_000;   // 2 min
    /** Watch the local Ollama server's reachability. */
    private boolean checkOllama = true;
    /** Critical disk threshold (%). Escalates above the proactive watcher's softer 90% nudge. */
    private int diskCriticalPercent = 95;
    /**
     * Optional command to (re)start Ollama when it's found down (e.g. {@code "brew services restart ollama"}
     * or {@code "open -a Ollama"}). Blank = only alert, never auto-run. Space-separated.
     */
    private String ollamaRestartCommand = "";
    /** Timeout for the Ollama health ping (ms). */
    private int ollamaPingTimeoutMs = 3000;
}
