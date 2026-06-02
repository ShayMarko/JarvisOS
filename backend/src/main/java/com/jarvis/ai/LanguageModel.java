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

    /** Short provider name for traces/observability (e.g. "mock", "claude"). */
    String name();
}
