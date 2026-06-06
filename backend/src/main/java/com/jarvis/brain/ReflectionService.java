package com.jarvis.brain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryDraft;
import com.jarvis.memory.MemoryService;
import com.jarvis.notification.NotificationService;
import com.jarvis.observability.AgentRunRecord;
import com.jarvis.observability.ObservabilityService;

import lombok.RequiredArgsConstructor;

/**
 * Nightly self-reflection: Jarvis reviews the day's runs (what was asked + outcomes) against what it
 * already remembers, and distils up to a few DURABLE lessons/preferences into memory — so it compounds
 * instead of repeating mistakes. Dedupes on write (skips a lesson it already holds) and posts a short
 * summary. Cheap model tier, only when a real model is configured.
 */
@Service
@RequiredArgsConstructor
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    private final JarvisReflectionProperties props;
    private final ObservabilityService observability;
    private final MemoryService memory;
    private final NotificationService notifications;
    private final LanguageModel model;
    private final JarvisAiProperties ai;

    @Scheduled(cron = "${jarvis.reflection.cron:0 30 23 * * *}", zone = "${jarvis.briefing.zone:}")
    void nightly() {
        if (!props.isEnabled() || !realModel()) {
            return;
        }
        try {
            List<AgentRunRecord> runs = observability.recent(props.getLookbackRuns());
            if (runs.isEmpty()) {
                return;   // nothing happened — nothing to learn
            }
            String runsText = summariseRuns(runs);
            String known = memory.list("").stream().map(Memory::getTitle).limit(40)
                    .reduce((a, b) -> a + "; " + b).orElse("(none)");

            ModelResponse r = model.generate(List.of(
                    ChatMessage.system("You are Jarvis reflecting at day's end. From the RUNS (what the user "
                            + "asked + the outcome) and what you ALREADY REMEMBER, extract up to " + props.getMaxLessons()
                            + " DURABLE lessons or preferences worth remembering to serve the user better tomorrow "
                            + "(a recurring need, a stated preference, a mistake to avoid). Skip anything already "
                            + "remembered or clearly one-off. Output STRICT lines, each exactly 'TITLE :: one-sentence "
                            + "lesson', or the single word NONE. No preamble."),
                    ChatMessage.user("RUNS:\n" + runsText + "\n\nALREADY REMEMBER: " + known)),
                    List.of(), cheapModelId());

            List<String[]> lessons = parseLessons(r == null ? null : r.text());
            int saved = 0;
            for (String[] lesson : lessons) {
                if (saved >= props.getMaxLessons() || alreadyKnown(lesson[0])) {
                    continue;
                }
                memory.create(new MemoryDraft("lesson", lesson[0], lesson[1], "reflection",
                        0.7, null, null, null, true));
                saved++;
            }
            // Self-editing memory: prune exact duplicates so the store stays tidy as it grows.
            int merged = memory.consolidate();

            if (saved > 0 || merged > 0) {
                StringBuilder body = new StringBuilder();
                if (saved > 0) {
                    String titles = lessons.stream().limit(saved).map(l -> "• " + l[0])
                            .reduce((a, b) -> a + "\n" + b).orElse("");
                    body.append("Learned ").append(saved).append(" new thing(s) for next time:\n").append(titles);
                }
                if (merged > 0) {
                    if (body.length() > 0) body.append('\n');
                    body.append("Tidied memory: removed ").append(merged).append(" duplicate(s).");
                }
                notifications.notify("info", "Jarvis nightly reflection", body.toString(), "reflection");
            }
        } catch (RuntimeException e) {
            log.debug("Nightly reflection skipped: {}", e.getMessage());
        }
    }

    private static String summariseRuns(List<AgentRunRecord> runs) {
        StringBuilder sb = new StringBuilder();
        for (AgentRunRecord r : runs) {
            String req = r.getRequest() == null ? "" : r.getRequest().replaceAll("\\s+", " ").strip();
            if (req.length() > 120) {
                req = req.substring(0, 119) + "…";
            }
            sb.append("- [").append(r.getStatus()).append("] ").append(req).append('\n');
        }
        return sb.toString().strip();
    }

    /** Parse 'TITLE :: CONTENT' lines into [title, content] pairs; tolerant of noise; NONE → empty. */
    static List<String[]> parseLessons(String text) {
        List<String[]> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.split("\n")) {
            String line = raw.strip().replaceFirst("^[-*\\d.\\s]+", "");
            if (line.isEmpty() || line.equalsIgnoreCase("NONE")) {
                continue;
            }
            int sep = line.indexOf("::");
            if (sep <= 0 || sep >= line.length() - 2) {
                continue;
            }
            String title = line.substring(0, sep).strip();
            String content = line.substring(sep + 2).strip();
            if (!title.isBlank() && !content.isBlank() && title.length() <= 80) {
                out.add(new String[]{title, content});
            }
        }
        return out;
    }

    private boolean alreadyKnown(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        return memory.list("").stream()
                .anyMatch(m -> m.getTitle() != null && m.getTitle().toLowerCase(Locale.ROOT).equals(t));
    }

    private boolean realModel() {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return p.equals("ollama")
                || ((p.equals("claude") || p.equals("anthropic")) && notBlank(ai.getAnthropicApiKey()))
                || (p.equals("openai") && notBlank(ai.getOpenaiApiKey()));
    }

    private String cheapModelId() {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return switch (p) {
            case "claude", "anthropic" -> ai.getPlannerModelClaude();
            case "openai" -> ai.getPlannerModelOpenai();
            default -> null;
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
