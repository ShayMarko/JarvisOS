package com.jarvis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jarvis.ai.TokenBudget;

/** The quality-preserving overlay: learning may promote a proven equal/higher-quality model, never downgrade. */
class LearnedRouterTest {

    private ModelRouter router;
    private OutcomeStatsService stats;
    private TokenBudget budget;
    private LearnedRouter learned;

    private final ModelDescriptor base = new ModelDescriptor("opus", "anthropic", false, 0.015, 0.075, 5, 900, true);

    @BeforeEach
    void setup() {
        router = mock(ModelRouter.class);
        stats = mock(OutcomeStatsService.class);
        budget = mock(TokenBudget.class);
        learned = new LearnedRouter(router, stats, budget);
        when(router.route(any(), any())).thenReturn(base);
        when(router.preference()).thenReturn(RoutingPreference.BALANCED);
        when(budget.shouldConserve()).thenReturn(false);
    }

    @Test
    void promotesProvenModelOfEqualOrHigherQuality() {
        ModelDescriptor proven = new ModelDescriptor("sonnet", "anthropic", false, 0.003, 0.015, 5, 700, true);
        when(stats.recommend("Code Agent")).thenReturn(Optional.of(proven));
        assertThat(learned.route("code", "Code Agent", "build it").id()).isEqualTo("sonnet");
    }

    @Test
    void neverDowngradesQuality() {
        ModelDescriptor weaker = new ModelDescriptor("ollama", "ollama", true, 0, 0, 3, 500, true);
        when(stats.recommend("Code Agent")).thenReturn(Optional.of(weaker));
        assertThat(learned.route("code", "Code Agent", "build it").id()).isEqualTo("opus");   // base kept
    }

    @Test
    void noHistoryKeepsBase() {
        when(stats.recommend(any())).thenReturn(Optional.empty());
        assertThat(learned.route("code", "Code Agent", "build it").id()).isEqualTo("opus");
    }

    @Test
    void conserveModeBypassesLearning() {
        when(budget.shouldConserve()).thenReturn(true);
        ModelDescriptor proven = new ModelDescriptor("sonnet", "anthropic", false, 0.003, 0.015, 5, 700, true);
        when(stats.recommend(any())).thenReturn(Optional.of(proven));
        assertThat(learned.route("code", "Code Agent", "x").id()).isEqualTo("opus");
    }

    @Test
    void explicitPreferenceBypassesLearning() {
        when(router.preference()).thenReturn(RoutingPreference.QUALITY);
        ModelDescriptor proven = new ModelDescriptor("sonnet", "anthropic", false, 0.003, 0.015, 5, 700, true);
        when(stats.recommend(any())).thenReturn(Optional.of(proven));
        assertThat(learned.route("code", "Code Agent", "x").id()).isEqualTo("opus");
    }
}
