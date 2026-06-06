package com.jarvis.brain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.reflection} — the nightly self-reflection pass: Jarvis reviews the day's runs and
 * what it already remembers, then distils durable lessons/preferences into memory so it compounds.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.reflection")
public class JarvisReflectionProperties {

    private boolean enabled = true;
    /** Cron for the nightly reflection (Spring 6-field). Default 23:30 daily. */
    private String cron = "0 30 23 * * *";
    /** How many recent runs to review. */
    private int lookbackRuns = 40;
    /** Max new lessons to record per night. */
    private int maxLessons = 3;
}
