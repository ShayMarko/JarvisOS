package com.jarvis.proactive;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.proactive} — the heartbeat. Background watchers that PROPOSE actions (via the
 * Notification Center) but never act on their own; the user decides.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.proactive")
public class JarvisProactiveProperties {

    private boolean enabled = true;
    /** Notify when disk usage crosses this percentage. */
    private int diskPercentThreshold = 90;
    /** How often the watchers run (ms). */
    private long checkIntervalMs = 600_000;   // 10 min
    /** Jarvis-root subfolders watched for newly-arrived files. */
    private List<String> watchFolders = List.of("Downloads", "Screenshots", "Generated");
}
