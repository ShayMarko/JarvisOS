package com.jarvis.ai;

import java.util.List;

/**
 * The model adapter boundary (spec §15: "SDKs are adapters"). The Brain and
 * agent runtime depend only on this interface; concrete providers (Mock,
 * Anthropic, …) are interchangeable behind it.
 */
public interface LanguageModel {

    ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools);

    /** Short provider name for traces/observability (e.g. "mock", "claude"). */
    String name();
}
