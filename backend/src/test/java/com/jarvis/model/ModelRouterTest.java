package com.jarvis.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ModelRouterTest {

    private final ModelDescriptor local = new ModelDescriptor("mock-local", "mock", true, 0, 0, 2, 5, true);
    private final ModelDescriptor haiku = new ModelDescriptor("haiku", "anthropic", false, 0.0008, 0.004, 3, 600, true);
    private final ModelDescriptor opus = new ModelDescriptor("opus", "anthropic", false, 0.015, 0.075, 5, 1500, true);
    private final List<ModelDescriptor> all = List.of(local, haiku, opus);

    @Test
    void qualityPrefersHighestQuality() {
        assertThat(ModelRouter.choose(all, RoutingPreference.QUALITY)).isEqualTo(opus);
    }

    @Test
    void cheapPrefersLowestCost() {
        assertThat(ModelRouter.choose(all, RoutingPreference.CHEAP)).isEqualTo(local);
    }

    @Test
    void privateChoosesLocalOnly() {
        assertThat(ModelRouter.choose(all, RoutingPreference.PRIVATE)).isEqualTo(local);
    }

    @Test
    void cheapFallsBackToOnlyAvailableWhenSingle() {
        assertThat(ModelRouter.choose(List.of(opus), RoutingPreference.CHEAP)).isEqualTo(opus);
    }
}
