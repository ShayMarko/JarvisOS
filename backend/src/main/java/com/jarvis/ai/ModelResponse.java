package com.jarvis.ai;

import java.util.List;

/**
 * A model's reply: either final {@code text}, or one or more {@code toolCalls}
 * the runtime must execute before asking the model again.
 */
public record ModelResponse(String text, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {

    public boolean wantsTools() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static ModelResponse text(String text, int prompt, int completion) {
        return new ModelResponse(text, List.of(), prompt, completion);
    }

    public static ModelResponse tools(List<ToolCall> calls, int prompt, int completion) {
        return new ModelResponse(null, calls, prompt, completion);
    }
}
