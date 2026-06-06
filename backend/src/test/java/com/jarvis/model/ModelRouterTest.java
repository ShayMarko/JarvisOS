package com.jarvis.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.TokenBudget;
import com.jarvis.model.ModelRouter.TaskTier;

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

    // --- per-task tiering (preference = BALANCED) ---

    @Test
    void balancedHeavyTaskPrefersQuality() {
        assertThat(ModelRouter.choose(all, RoutingPreference.BALANCED, TaskTier.HEAVY)).isEqualTo(opus);
    }

    @Test
    void balancedLightTaskPrefersCheap() {
        assertThat(ModelRouter.choose(all, RoutingPreference.BALANCED, TaskTier.LIGHT)).isEqualTo(local);
    }

    @Test
    void conserveForcesCheapLocal() {
        // Near the budget cap / paused → keep it on the free local model regardless of tier.
        assertThat(ModelRouter.choose(all, RoutingPreference.BALANCED, TaskTier.HEAVY, true)).isEqualTo(local);
    }

    @Test
    void agentSlugsMapToTiers() {
        assertThat(ModelRouter.tierFor("code")).isEqualTo(TaskTier.HEAVY);
        assertThat(ModelRouter.tierFor("system")).isEqualTo(TaskTier.LIGHT);
        assertThat(ModelRouter.tierFor("general")).isEqualTo(TaskTier.STANDARD);
    }

    // --- end-to-end route() over a real catalogue (cross-provider, mock-avoiding) ---

    @Test
    void routeNeverPicksMockWhenARealModelExists() {
        JarvisAiProperties props = new JarvisAiProperties();   // default: ollama model present, no cloud keys
        ModelRouter r = new ModelRouter(new ModelCatalog(props), props, new TokenBudget(props));
        assertThat(r.route("code").provider()).isEqualTo("ollama");   // not "mock"
    }

    @Test
    void routePicksHeavyCloudModelForCodeButStaysLocalForLightWork() {
        JarvisAiProperties props = new JarvisAiProperties();
        props.setAnthropicApiKey("sk-test");                  // makes the Claude tiers available
        ModelRouter r = new ModelRouter(new ModelCatalog(props), props, new TokenBudget(props));
        assertThat(r.route("code").id()).isEqualTo("claude-opus-4-8");   // heavy → best quality
        assertThat(r.route("system").provider()).isEqualTo("ollama");    // light → cheapest (free local)
    }

    // --- content-aware classification (the cost lever for the general agent) ---

    @Test
    void classifyEscalatesHardMessagesToHeavy() {
        assertThat(ModelRouter.classify("general", "build me a Spring Boot SaaS app")).isEqualTo(TaskTier.HEAVY);
        assertThat(ModelRouter.classify("general", "please refactor this and fix the bug")).isEqualTo(TaskTier.HEAVY);
    }

    @Test
    void classifyDropsTrivialGeneralTurnsToLight() {
        assertThat(ModelRouter.classify("general", "hi")).isEqualTo(TaskTier.LIGHT);
        assertThat(ModelRouter.classify("general", "what time is it")).isEqualTo(TaskTier.LIGHT);
    }

    @Test
    void classifyKeepsHeavyAgentHeavyEvenOnShortMessage() {
        assertThat(ModelRouter.classify("code", "x")).isEqualTo(TaskTier.HEAVY);
    }

    @Test
    void classifyKeepsMediumGeneralTurnStandard() {
        assertThat(ModelRouter.classify("general", "tell me about the weather in tel aviv right now"))
                .isEqualTo(TaskTier.STANDARD);
    }

    @Test
    void contentAwareRouteEscalatesGeneralBuildToOpusButKeepsChatLocal() {
        JarvisAiProperties props = new JarvisAiProperties();
        props.setAnthropicApiKey("sk-test");
        ModelRouter r = new ModelRouter(new ModelCatalog(props), props, new TokenBudget(props));
        assertThat(r.route("general", "build me a full Spring Boot app").id()).isEqualTo("claude-opus-4-8");
        assertThat(r.route("general", "hi").provider()).isEqualTo("ollama");
    }
}
