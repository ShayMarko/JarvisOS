package com.jarvis.ai;

import java.util.List;

/**
 * The model adapter boundary (spec §15: "SDKs are adapters"). The Brain and
 * agent runtime depend only on this interface; concrete providers (Mock,
 * Anthropic, …) are interchangeable behind it.
 */
public interface LanguageModel {

    ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools);

    /**
     * Generate with a per-call model override (e.g. route the lightweight planner to a
     * cheap model). The default ignores the override; HTTP adapters honor it. A
     * {@code null}/blank override means "use the configured default model".
     */
    default ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools, String modelOverride) {
        return generate(messages, tools);
    }

    /**
     * Generate on a SPECIFIC provider + model, chosen per task by the Model Router. The default just
     * applies the model id on the single configured provider; {@link ProviderSwitchingLanguageModel}
     * overrides this to actually switch the backing provider (Ollama / OpenAI / Anthropic) per call.
     */
    default ModelResponse generateOn(String provider, String modelId,
                                     List<ChatMessage> messages, List<ToolSpec> tools) {
        return generate(messages, tools, modelId);
    }

    /** Short provider name for traces/observability (e.g. "mock", "claude"). */
    String name();
}
