package com.jarvis.newsletter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.newsletter} — the recurring newsletter income lane. DEFAULT OFF. When armed, a job
 * dispatches the Newsletter Studio agent on a schedule to research + write (+ send, approval-gated) an issue.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.newsletter")
public class JarvisNewsletterProperties {

    /** Master switch — OFF by default. */
    private boolean enabled = false;
    /** When an issue is produced (Spring 6-field cron). Default: Tuesday 09:00. */
    private String cron = "0 0 9 * * TUE";
    /** The newsletter's theme/niche (blank = skip; nothing to write about). */
    private String topic = "";
}
