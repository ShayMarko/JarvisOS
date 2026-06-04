package com.jarvis.watchdog;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.notification.NotificationService;
import com.jarvis.system.SystemMonitorService;

import lombok.RequiredArgsConstructor;

/**
 * The watchdog — keeps the headless box healthy. Each tick it checks the local Ollama server and free
 * disk; on a real failure it alerts the owner (Notification Center → Discord/Telegram) ONCE per incident
 * (dedup + hysteresis, like the proactive watcher) and announces recovery. Can optionally auto-restart
 * Ollama. It cannot resurrect itself if the JVM dies — pair it with an OS keepalive (launchd) for that.
 */
@Service
@RequiredArgsConstructor
public class WatchdogService {

    private static final Logger log = LoggerFactory.getLogger(WatchdogService.class);

    private final JarvisWatchdogProperties props;
    private final JarvisAiProperties ai;
    private final SystemMonitorService monitor;
    private final NotificationService notifications;

    private final AtomicBoolean ollamaDown = new AtomicBoolean(false);
    private final AtomicBoolean diskCritical = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${jarvis.watchdog.check-interval-ms:120000}", initialDelay = 90_000)
    void tick() {
        if (!props.isEnabled()) {
            return;
        }
        if (props.isCheckOllama()) {
            try { evaluateOllama(pingOllama()); } catch (RuntimeException e) { log.debug("ollama check failed: {}", e.getMessage()); }
        }
        try { evaluateDisk(diskUsedPercent()); } catch (RuntimeException e) { log.debug("disk check failed: {}", e.getMessage()); }
    }

    /** Alert once when Ollama goes down (optionally auto-restart); announce recovery when it returns. */
    void evaluateOllama(boolean up) {
        if (!up) {
            if (ollamaDown.compareAndSet(false, true)) {
                String restart = maybeRestartOllama();
                notifications.notify("critical", "Ollama is down",
                        "The local model server at " + ai.getOllamaBaseUrl() + " isn't responding. " + restart, "watchdog");
            }
        } else if (ollamaDown.compareAndSet(true, false)) {
            notifications.notify("info", "Ollama recovered", "The local model server is responding again.", "watchdog");
        }
    }

    /** Alert once when disk crosses the critical line; re-arm (and announce recovery) when it drops back. */
    void evaluateDisk(int usedPct) {
        if (usedPct >= props.getDiskCriticalPercent()) {
            if (diskCritical.compareAndSet(false, true)) {
                notifications.notify("critical", "Disk critically full",
                        "Disk is " + usedPct + "% full. Free space now — writes may start failing.", "watchdog");
            }
        } else if (usedPct < props.getDiskCriticalPercent() - 3 && diskCritical.compareAndSet(true, false)) {
            notifications.notify("info", "Disk recovered", "Disk usage is back under control (" + usedPct + "%).", "watchdog");
        }
    }

    /** True if the local Ollama server answers. Package-private so tests can drive evaluateOllama directly. */
    boolean pingOllama() {
        try {
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(Duration.ofMillis(props.getOllamaPingTimeoutMs()));
            rf.setReadTimeout(Duration.ofMillis(props.getOllamaPingTimeoutMs()));
            RestClient http = RestClient.builder().requestFactory(rf).baseUrl(ai.getOllamaBaseUrl()).build();
            http.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int diskUsedPercent() {
        Object diskObj = monitor.snapshot().get("disk");
        if (!(diskObj instanceof Map<?, ?> disk)) {
            return 0;
        }
        long total = num(disk.get("totalBytes"));
        long free = num(disk.get("freeBytes"));
        return total <= 0 ? 0 : (int) Math.round((total - free) * 100.0 / total);
    }

    /** Best-effort Ollama restart when a command is configured. Returns a human note for the alert. */
    private String maybeRestartOllama() {
        String cmd = props.getOllamaRestartCommand();
        if (cmd == null || cmd.isBlank()) {
            return "Auto-restart is off (set jarvis.watchdog.ollama-restart-command to enable).";
        }
        try {
            new ProcessBuilder(cmd.trim().split("\\s+")).redirectErrorStream(true).start();
            log.info("Watchdog attempted to restart Ollama: {}", cmd);
            return "Attempting an automatic restart…";
        } catch (Exception e) {
            return "Auto-restart failed: " + e.getMessage();
        }
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
