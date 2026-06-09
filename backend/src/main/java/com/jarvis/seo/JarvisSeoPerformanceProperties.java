package com.jarvis.seo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.seo-performance} — the autonomous SEO double-down loop. DEFAULT OFF; arm it by setting
 * a Plausible site + enabling. When on, a weekly job reads real traffic and steers the SEO agent to compound
 * winners and cut losers. Reads are free (Plausible); any publish/deploy the agent triggers is still
 * approval-gated like every other write.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.seo-performance")
public class JarvisSeoPerformanceProperties {

    /** Master switch — OFF by default (no scheduled work until armed). */
    private boolean enabled = false;
    /** When the weekly review runs (Spring 6-field cron). Default: Monday 12:00. */
    private String cron = "0 0 12 * * MON";
    /** The Plausible site domain to review (blank = skip; nothing to analyse). */
    private String site = "";
    /** Look-back window for Plausible stats (e.g. 7d, 30d). */
    private String period = "30d";
    /** How many top pages to focus the double-down on. */
    private int topPages = 5;
}
