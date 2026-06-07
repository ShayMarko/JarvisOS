package com.jarvis.proactive;

import com.jarvis.ai.ModelTier;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.digest.DigestService;
import com.jarvis.notification.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * The proactive INITIATIVE pass — Jarvis looking out for the owner without being asked. On a cron it
 * reviews current state (the "Jarvis Today" digest: calendar, pending approvals, tasks, recent activity)
 * and asks the model whether anything is genuinely worth flagging. High-signal suggestions are pushed to
 * the Notification Center (which mirrors to Discord/Telegram); if nothing's worth interrupting, it stays
 * quiet. Runs on the cheap model tier and only when a real model is configured — never the offline mock.
 */
@Service
@RequiredArgsConstructor
public class InitiativeService {

    private static final Logger log = LoggerFactory.getLogger(InitiativeService.class);

    private final JarvisProactiveProperties props;
    private final DigestService digest;
    private final LanguageModel model;
    private final NotificationService notifications;
    private final JarvisAiProperties ai;

    @Scheduled(cron = "${jarvis.proactive.initiative-cron:0 0 9,18 * * *}", zone = "${jarvis.briefing.zone:}")
    void tick() {
        if (!props.isEnabled() || !props.isInitiative() || !ModelTier.isReal(ai)) {
            return;
        }
        try {
            ModelResponse r = model.generate(List.of(
                    ChatMessage.system("You are Jarvis, proactively looking out for your owner. From the STATUS "
                            + "below, list 0-3 SHORT, HIGH-SIGNAL items worth flagging or doing right now — an "
                            + "overdue/stuck task, a pending approval, a health/disk issue, a useful follow-up. One "
                            + "concise line each. If nothing genuinely warrants interrupting them, reply with exactly "
                            + "NONE. No preamble, no headings."),
                    ChatMessage.user(digest.build())), List.of(), ModelTier.cheapModelId(ai));
            String text = r == null || r.text() == null ? "" : r.text().strip();
            if (text.isEmpty() || text.length() < 8 || text.equalsIgnoreCase("NONE")
                    || text.toUpperCase().startsWith("NONE")) {
                return;   // nothing worth interrupting for — stay quiet
            }
            notifications.notify("info", "Jarvis — proactive check", text, "initiative");
        } catch (RuntimeException e) {
            log.debug("Initiative pass skipped: {}", e.getMessage());
        }
    }

    /** A real reasoning model is active (not the offline mock) — otherwise the pass is pointless. */

    /** Cheap model tier for this background pass (planner model); null ⇒ provider default. */

}
