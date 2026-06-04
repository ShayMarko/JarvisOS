package com.jarvis.digest;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.ai.tools.RssTool;
import com.jarvis.discord.DiscordService;
import com.jarvis.observability.ObservabilityService;
import com.jarvis.revenue.RevenueService;
import com.jarvis.system.SystemMonitorService;

import lombok.RequiredArgsConstructor;

/**
 * Pushes the "Jarvis Today" briefing to the private Discord channel on a daily cron — the morning
 * heartbeat for the headless box. Reuses {@link DigestService} (calendar, approvals, tasks, activity)
 * and enriches it with weather, money (token spend + ROI), top news headlines, a system-health line,
 * and an AI "what matters today" summary. Every enrichment is best-effort: if a section fails or isn't
 * configured it's simply omitted, never breaking the briefing. No-op until the Discord channel is set.
 */
@Service
@RequiredArgsConstructor
public class BriefingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BriefingScheduler.class);

    private final JarvisBriefingProperties props;
    private final DigestService digest;
    private final DiscordService discord;
    private final SystemMonitorService monitor;
    private final RevenueService revenue;
    private final ObservabilityService observability;
    private final WeatherService weather;
    private final RssTool rss;
    private final LanguageModel model;

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
        String body = buildBody();
        if (props.isAiSummary()) {
            String summary = aiSummary(body);
            if (summary != null && !summary.isBlank()) {
                return "☀️ Morning briefing\n\n🧠 " + summary.strip() + "\n\n" + body;
            }
        }
        return "☀️ Morning briefing\n\n" + body;
    }

    /** The full briefing body: digest + weather + money + news + system health. */
    private String buildBody() {
        StringBuilder sb = new StringBuilder(digest.build());
        String w = weather();
        if (!w.isBlank()) {
            sb.append("\n\n☁️ Weather\n  ").append(w);
        }
        if (props.isIncludeMoney()) {
            String money = money();
            if (!money.isBlank()) {
                sb.append("\n\n").append(money);
            }
        }
        String news = news();
        if (!news.isBlank()) {
            sb.append("\n\n📰 News\n").append(news);
        }
        if (props.isIncludeSystem()) {
            sb.append("\n\n").append(health());
        }
        return sb.toString();
    }

    /** Token spend + ROI, both best-effort. */
    private String money() {
        try {
            Map<String, Object> roi = revenue.roi();
            Map<String, Object> cost = observability.costSummary(200);
            if (roi == null || cost == null) {
                return "";
            }
            String covers = Boolean.TRUE.equals(roi.get("coversCost")) ? " ✅" : "";
            return "💰 Money & usage\n"
                    + "  • ROI " + roi.get("roi") + "× — value $" + roi.get("valueGenerated")
                    + " vs cost $" + roi.get("monthlyCost") + covers + "\n"
                    + "  • AI spend: $" + cost.get("totalCost") + " over " + cost.get("totalTokens") + " tokens";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Keyless current weather, if a location is configured. */
    private String weather() {
        Double lat = props.getWeatherLat();
        Double lon = props.getWeatherLon();
        if (lat == null || lon == null) {
            return "";
        }
        String line = weather.current(lat, lon);
        if (line.isBlank()) {
            return "";
        }
        String place = props.getWeatherPlace();
        return (place == null || place.isBlank() ? "" : place + ": ") + line;
    }

    /** Top headlines from the configured RSS feeds (best-effort, capped). */
    private String news() {
        List<String> feeds = props.getRssFeeds();
        if (feeds == null || feeds.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int feedsUsed = 0;
        for (String feed : feeds) {
            if (feedsUsed >= 3) {
                break;   // keep the briefing tight
            }
            try {
                String out = rss.execute("{\"url\":\"" + feed + "\",\"limit\":3}");
                if (out != null && !out.isBlank() && !out.startsWith("Couldn't") && !out.startsWith("Provide")) {
                    out.lines().limit(3).forEach(l -> sb.append("  • ").append(l.strip()).append('\n'));
                    feedsUsed++;
                }
            } catch (RuntimeException ignored) {
                // skip a bad feed
            }
        }
        return sb.toString().strip();
    }

    /** One short AI summary of the whole briefing — "what matters today". Free on Ollama; best-effort. */
    private String aiSummary(String body) {
        try {
            ModelResponse r = model.generate(List.of(
                    ChatMessage.system("You are Jarvis. In 2-3 short sentences, tell the user what matters most "
                            + "today based on this briefing. Prioritise actions and anything time-sensitive. "
                            + "No preamble, no markdown headers — just the sentences."),
                    ChatMessage.user(body)), List.of());
            return r == null ? null : r.text();
        } catch (RuntimeException e) {
            log.debug("Briefing AI summary skipped: {}", e.getMessage());
            return null;
        }
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
