package com.jarvis.digest;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.briefing} — the scheduled morning briefing pushed to the private Discord channel.
 * It's the "Jarvis Today" digest, delivered to your phone automatically each morning (no-op until the
 * Discord channel is configured).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.briefing")
public class JarvisBriefingProperties {

    private boolean enabled = true;
    /** Cron for the daily push (Spring 6-field cron). Default 08:00 every day. */
    private String cron = "0 0 8 * * *";
    /** Time zone for the cron (blank = server default). */
    private String zone = "";
    /** Append a one-line system-health summary (CPU/RAM/disk). */
    private boolean includeSystem = true;
}
