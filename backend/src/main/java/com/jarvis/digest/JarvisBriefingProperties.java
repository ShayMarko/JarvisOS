package com.jarvis.digest;

import java.util.List;

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

    /** Append token spend + ROI (does Jarvis out-earn its cost). */
    private boolean includeMoney = true;

    /** Prepend a short AI "what matters today" summary of the whole briefing (free on Ollama). */
    private boolean aiSummary = true;

    /** Weather location (keyless open-meteo). Both lat+lon must be set, else the weather line is skipped. */
    private Double weatherLat;
    private Double weatherLon;
    /** Optional human label for the location, e.g. "Tel Aviv". */
    private String weatherPlace = "";

    /** RSS/Atom feed URLs to pull top headlines from. Empty = no news section. */
    private List<String> rssFeeds = List.of();
}
