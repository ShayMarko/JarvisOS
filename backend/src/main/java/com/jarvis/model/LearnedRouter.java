package com.jarvis.model;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.jarvis.ai.TokenBudget;

import lombok.RequiredArgsConstructor;

/**
 * A thin, quality-preserving overlay on {@link ModelRouter}: it starts from the base router's choice and
 * only swaps in a history-proven model (from {@link OutcomeStatsService}) when that model is at least as
 * high quality as the base pick. So learning can save cost by promoting a cheaper model that keeps
 * succeeding, but it can NEVER downgrade a heavy task to a weaker model just because the weaker one was
 * "OK" on easier past work. Explicit preferences and budget-conserve mode bypass learning entirely.
 */
@Component
@RequiredArgsConstructor
public class LearnedRouter {

    private final ModelRouter router;
    private final OutcomeStatsService stats;
    private final TokenBudget budget;

    /** Route for an agent (slug + display name) and message, applying the learned overlay. */
    public ModelDescriptor route(String slug, String agentName, String message) {
        ModelDescriptor base = router.route(slug, message);
        if (base == null) {
            return null;
        }
        // Respect explicit policy + budget conservation — don't let learning override those.
        if (router.preference() != RoutingPreference.BALANCED || (budget != null && budget.shouldConserve())) {
            return base;
        }
        Optional<ModelDescriptor> learned = stats.recommend(agentName);
        if (learned.isPresent()) {
            ModelDescriptor l = learned.get();
            if (l.available() && l.quality() >= base.quality()) {
                return l;   // proven model, no quality regression → prefer it
            }
        }
        return base;
    }
}
