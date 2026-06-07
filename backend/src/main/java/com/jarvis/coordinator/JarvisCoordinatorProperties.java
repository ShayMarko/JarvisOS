package com.jarvis.coordinator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.coordinator} — the autonomous "CEO" loop that runs the income funnel toward a
 * north-star goal. DEFAULT OFF: turning it on lets Jarvis pick and execute one funnel action per cron
 * (scout an idea, build a product, draft marketing, analyse results) without being asked. It deliberately
 * stops short of publishing/charging — selling + deploying stay the owner's call — and every paid action
 * is still bounded by the monthly USD cap.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.coordinator")
public class JarvisCoordinatorProperties {

    /** Master switch. OFF by default — autonomy is opt-in. */
    private boolean enabled = false;
    /** Cron for the autonomous pass (Spring 6-field). Default 10:00 daily. */
    private String cron = "0 0 10 * * *";
    /** The north-star the coordinator plans toward (e.g. "Reach $500/mo recurring across the product portfolio"). */
    private String goal = "Grow the product portfolio toward steady passive income — build small useful products, "
            + "improve listings, and market what's already live.";
    /** How many funnel actions to dispatch per pass (kept small so the loop stays observable). */
    private int maxActionsPerRun = 1;
}
