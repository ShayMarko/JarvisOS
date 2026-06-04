package com.jarvis.digest;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.discord.DiscordService;
import com.jarvis.system.SystemMonitorService;

import lombok.RequiredArgsConstructor;

/**
 * Pushes the "Jarvis Today" briefing to the private Discord channel on a daily cron — the morning
 * heartbeat for the headless box. Reuses {@link DigestService} (calendar, approvals, tasks, activity)
 * and appends a one-line system-health summary. No-op until the Discord channel is configured.
 */
@Service
@RequiredArgsConstructor
public class BriefingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BriefingScheduler.class);

    private final JarvisBriefingProperties props;
    private final DigestService digest;
    private final DiscordService discord;
    private final SystemMonitorService monitor;

    @Scheduled(cron = "${jarvis.briefing.cron:0 0 8 * * *}", zone = "${jarvis.briefing.zone:}")
    void daily() {
        if (!props.isEnabled()) {
            return;
        }
        try {
            discord.push(compose());
        } catch (RuntimeException e) {
            log.debug("Morning briefing push failed: {}", e.getMessage());
        }
    }

    /** Build the briefing text (package-private so a test can assert it without the scheduler/network). */
    String compose() {
        StringBuilder sb = new StringBuilder("☀️ Morning briefing\n\n").append(digest.build());
        if (props.isIncludeSystem()) {
            sb.append("\n\n").append(health());
        }
        return sb.toString();
    }

    private String health() {
        try {
            Map<String, Object> snap = monitor.snapshot();
            Map<?, ?> cpu = asMap(snap.get("cpu"));
            Map<?, ?> mem = asMap(snap.get("memory"));
            Map<?, ?> disk = asMap(snap.get("disk"));
            int cpuPct = (int) Math.round(num(cpu.get("systemCpuLoad")) * 100);
            long memUsed = (long) num(mem.get("usedPhysicalBytes"));
            long memTotal = (long) num(mem.get("totalPhysicalBytes"));
            long dTotal = (long) num(disk.get("totalBytes"));
            long dFree = (long) num(disk.get("freeBytes"));
            int diskPct = dTotal > 0 ? (int) Math.round((dTotal - dFree) * 100.0 / dTotal) : 0;
            return "🖥️ System: CPU " + cpuPct + "%, RAM " + gb(memUsed) + "/" + gb(memTotal) + " GB, disk " + diskPct + "% used";
        } catch (RuntimeException e) {
            return "🖥️ System: (status unavailable)";
        }
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map<?, ?> m ? m : Map.of();
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static long gb(long bytes) {
        return Math.round(bytes / 1_000_000_000.0);
    }
}
