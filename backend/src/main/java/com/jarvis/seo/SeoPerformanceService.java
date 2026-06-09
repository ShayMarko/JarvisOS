package com.jarvis.seo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.audit.AuditService;
import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.connectors.ConnectorRegistry;
import com.jarvis.notification.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * The SEO performance loop — closes the "we built it but never iterated" gap. On a schedule (default OFF) it
 * pulls REAL traffic from Plausible, then hands the SEO agent a concrete brief: compound the winning pages
 * with follow-up articles and cut/rewrite the losers. Brain-decides, code-bounds: this service only gathers
 * the numbers and dispatches; the agent does the creative work and any publish stays approval-gated.
 */
@Service
@RequiredArgsConstructor
public class SeoPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(SeoPerformanceService.class);

    private final JarvisSeoPerformanceProperties props;
    private final ConnectorRegistry connectors;
    private final ObjectProvider<Orchestrator> orchestratorProvider;   // lazy → no eager bean cycle
    private final NotificationService notifications;
    private final AuditService audit;

    @Scheduled(cron = "${jarvis.seo-performance.cron:0 0 12 * * MON}", zone = "${jarvis.briefing.zone:}")
    void scheduled() {
        if (props.isEnabled()) {
            review();
        }
    }

    /** Run one SEO review now (also the on-demand path). Safe when dormant: returns a message, never throws. */
    public String review() {
        String site = props.getSite();
        if (site == null || site.isBlank()) {
            return "No site configured (set jarvis.seo-performance.site).";
        }
        String args = "{\"site\":\"" + site + "\",\"period\":\"" + props.getPeriod() + "\"}";
        String stats;
        String topPages;
        try {
            stats = connectors.invoke("plausible", "stats", args);
            topPages = connectors.invoke("plausible", "top_pages", args);
        } catch (Exception e) {
            log.info("SEO review skipped — Plausible unavailable: {}", e.getMessage());
            return "Plausible isn't connected (add the plausible-token secret to enable SEO reviews).";
        }
        String instruction = buildInstruction(site, stats, topPages, props.getTopPages());
        ChatResponse resp = orchestratorProvider.getObject().handle(instruction, "seo-performance");
        String answer = resp == null || resp.answer() == null ? "" : resp.answer();
        notifications.notify("info", "SEO performance — " + site, preview(answer), "seo-performance");
        audit.record("SEO", "seo:review", site, "OK", "period=" + props.getPeriod());
        log.info("SEO performance review dispatched for {}", site);
        return answer;
    }

    /** The double-down brief handed to the SEO agent (pure + testable). */
    static String buildInstruction(String site, String stats, String topPages, int topN) {
        return "Review this period's SEO performance for " + site + " and DOUBLE DOWN on what works.\n\n"
                + "Site stats:\n" + stats + "\n\nTop pages:\n" + topPages + "\n\n"
                + "Identify the top " + topN + " winning pages and any clear losers. For each winner, create "
                + "1-2 follow-up articles (use create_article_page) targeting adjacent keywords so the traffic "
                + "compounds. For losers, recommend cutting or rewriting them. Record any experiment with "
                + "ab_test. Be concrete and act, don't just describe.";
    }

    private static String preview(String s) {
        if (s == null || s.isBlank()) {
            return "(no output)";
        }
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() <= 280 ? one : one.substring(0, 280) + "…";
    }
}
