package com.jarvis.newsletter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.audit.AuditService;
import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.notification.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * The recurring newsletter income lane — gives the Newsletter Studio agent an autonomous cadence (default
 * OFF). On schedule it dispatches the agent to research + write this period's issue; sending stays
 * approval-gated. Brain-decides / code-bounds, mirroring the SEO performance loop.
 */
@Service
@RequiredArgsConstructor
public class NewsletterService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterService.class);

    private final JarvisNewsletterProperties props;
    private final ObjectProvider<Orchestrator> orchestratorProvider;   // lazy → no eager bean cycle
    private final NotificationService notifications;
    private final AuditService audit;

    @Scheduled(cron = "${jarvis.newsletter.cron:0 0 9 * * TUE}", zone = "${jarvis.briefing.zone:}")
    void scheduled() {
        if (props.isEnabled()) {
            produceIssue();
        }
    }

    /** Produce one issue now (also the on-demand path). Safe no-op when no topic is configured. */
    public String produceIssue() {
        String topic = props.getTopic();
        if (topic == null || topic.isBlank()) {
            return "No newsletter topic configured (set jarvis.newsletter.topic).";
        }
        String instruction = buildInstruction(topic);
        ChatResponse resp = orchestratorProvider.getObject().handle(instruction, "newsletter");
        String answer = resp == null || resp.answer() == null ? "" : resp.answer();
        notifications.notify("info", "Newsletter issue — " + topic, preview(answer), "newsletter");
        audit.record("NEWSLETTER", "newsletter:issue", topic, "OK", null);
        log.info("Newsletter issue dispatched for topic '{}'", topic);
        return answer;
    }

    /** The brief handed to the Newsletter Studio agent (pure + testable). */
    static String buildInstruction(String topic) {
        return "Produce this period's newsletter issue about \"" + topic + "\". Research the freshest, "
                + "highest-signal stories now (web_search / rss_fetch), check memory so you don't repeat past "
                + "issues, write one tight issue (hooky subject + 3-6 curated items + one CTA), save it with "
                + "write_file under Newsletters/, and if the resend connector is connected, send it (it's "
                + "approval-gated). Record the issue topic in memory so the next one compounds.";
    }

    private static String preview(String s) {
        if (s == null || s.isBlank()) {
            return "(no output)";
        }
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() <= 280 ? one : one.substring(0, 280) + "…";
    }
}
