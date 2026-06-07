package com.jarvis.ai;

/**
 * Shared helpers for reasoning about the active model tier — single source of truth for "is a REAL model
 * configured?" and "which cheap/planner model id to use for a background pass?". Previously these two methods
 * were copy-pasted into every service that runs a background LLM pass (Orchestrator, AgentRuntime, EvalService,
 * InitiativeService, ReflectionService, CoordinatorService); consolidated here so the rules can't drift.
 */
public final class ModelTier {

    private ModelTier() {}

    /** True when a genuine reasoning model is active (local Ollama, or a keyed Claude/OpenAI) — not the offline mock. */
    public static boolean isReal(JarvisAiProperties ai) {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return p.equals("ollama")
                || ((p.equals("claude") || p.equals("anthropic")) && notBlank(ai.getAnthropicApiKey()))
                || (p.equals("openai") && notBlank(ai.getOpenaiApiKey()));
    }

    /** The cheap/planner model id for background passes; {@code null} ⇒ let the provider use its default. */
    public static String cheapModelId(JarvisAiProperties ai) {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return switch (p) {
            case "claude", "anthropic" -> ai.getPlannerModelClaude();
            case "openai" -> ai.getPlannerModelOpenai();
            default -> null;
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
