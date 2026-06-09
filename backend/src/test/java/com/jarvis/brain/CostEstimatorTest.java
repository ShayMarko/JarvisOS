package com.jarvis.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.jarvis.model.ModelDescriptor;
import com.jarvis.model.ModelRouter;

/** Cost-aware planning: rough up-front estimates; free/local models cost $0. */
class CostEstimatorTest {

    @Test
    void promptTokenEstimateHasOverheadPlusFourCharsPerToken() {
        assertThat(CostEstimator.estimatePromptTokens("")).isEqualTo(600);
        assertThat(CostEstimator.estimatePromptTokens("x".repeat(400))).isEqualTo(700);
    }

    @Test
    void paidModelTaskCostsMoreThanZero() {
        ModelRouter router = mock(ModelRouter.class);
        when(router.route(any(), any()))
                .thenReturn(new ModelDescriptor("opus", "anthropic", false, 0.015, 0.075, 5, 900, true));
        double cost = new CostEstimator(router).estimateTaskCost("code", "build an app");
        assertThat(cost).isGreaterThan(0.0);
    }

    @Test
    void freeLocalModelTaskCostsZero() {
        ModelRouter router = mock(ModelRouter.class);
        when(router.route(any(), any()))
                .thenReturn(new ModelDescriptor("ollama", "ollama", true, 0, 0, 3, 500, true));
        double cost = new CostEstimator(router).estimateTaskCost("code", "build an app");
        assertThat(cost).isEqualTo(0.0);
    }
}
