package com.jarvis.proactive;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.notification.NotificationService;
import com.jarvis.system.SystemMonitorService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * The heartbeat — background watchers that make Jarvis PROACTIVE. They notice things (disk filling up,
 * new files arriving) and PROPOSE an action via the Notification Center (which also pushes to your
 * phone over Telegram). They never act unasked — you approve. Smart-and-automatic, not a cron robot:
 * each watcher de-dupes so it nudges once per real change, not every tick.
 */
@Service
@RequiredArgsConstructor
public class ProactiveWatcherService {

    private static final Logger log = LoggerFactory.getLogger(ProactiveWatcherService.class);

    private final SystemMonitorService monitor;
    private final NotificationService notifications;
    private final JarvisFileSystemProperties fsProps;
    private final JarvisProactiveProperties props;

    private final AtomicBoolean diskAlerted = new AtomicBoolean(false);
    private volatile Instant lastFileScan = Instant.now();

    @PostConstruct
    void init() {
        lastFileScan = Instant.now();   // ignore files that already exist at startup
        log.info("Proactive watchers {} (every {} ms).", props.isEnabled() ? "on" : "off", props.getCheckIntervalMs());
    }

    @Scheduled(fixedDelayString = "${jarvis.proactive.check-interval-ms:600000}", initialDelay = 60_000)
    void tick() {
        if (!props.isEnabled()) {
            return;
        }
        try { checkDisk(); } catch (RuntimeException e) { log.debug("disk watch failed: {}", e.getMessage()); }
        try { checkNewFiles(); } catch (RuntimeException e) { log.debug("file watch failed: {}", e.getMessage()); }
    }

    /** Nudge once when disk usage crosses the threshold; reset (hysteresis) once it drops back. */
    void checkDisk() {
        Object diskObj = monitor.snapshot().get("disk");
        if (!(diskObj instanceof Map<?, ?> disk)) {
            return;
        }
        long total = num(disk.get("totalBytes"));
        long free = num(disk.get("freeBytes"));
        if (total <= 0) {
            return;
        }
        int usedPct = (int) Math.round((total - free) * 100.0 / total);
        if (usedPct >= props.getDiskPercentThreshold()) {
            if (diskAlerted.compareAndSet(false, true)) {
                notifications.notify("warning", "Disk almost full",
                        "Your disk is " + usedPct + "% full (" + gb(free) + " GB free). Want me to clean up Downloads or old files?",
                        "proactive");
            }
        } else if (usedPct < props.getDiskPercentThreshold() - 5) {
            diskAlerted.set(false);   // recovered — re-arm
        }
    }

    /** Notice files that arrived since the last scan and propose organizing them. */
    void checkNewFiles() {
        Instant since = lastFileScan;
        Path root = expandHome(fsProps.getJarvisRoot());
        List<String> found = new ArrayList<>();
        for (String folder : props.getWatchFolders()) {
            Path dir = root.resolve(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.list(dir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> modifiedAfter(p, since))
                        .forEach(p -> found.add(folder + "/" + p.getFileName()));
            } catch (Exception ignored) {
                // folder unreadable — skip
            }
        }
        lastFileScan = Instant.now();
        if (!found.isEmpty()) {
            String sample = String.join(", ", found.subList(0, Math.min(3, found.size())));
            notifications.notify("info", found.size() + " new file(s) noticed",
                    "New: " + sample + (found.size() > 3 ? " …" : "") + ". Want me to organize or file them?",
                    "proactive");
        }
    }

    private static boolean modifiedAfter(Path p, Instant since) {
        try {
            return Files.getLastModifiedTime(p).toInstant().isAfter(since);
        } catch (Exception e) {
            return false;
        }
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static long gb(long bytes) {
        return Math.round(bytes / 1_000_000_000.0);
    }

    private static Path expandHome(String raw) {
        if (raw != null && raw.startsWith("~")) {
            return Path.of(System.getProperty("user.home") + raw.substring(1)).toAbsolutePath().normalize();
        }
        return Path.of(raw == null ? "." : raw).toAbsolutePath().normalize();
    }
}
