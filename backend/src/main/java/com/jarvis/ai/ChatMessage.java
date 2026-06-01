package com.jarvis.ai;

import java.util.List;

/**
 * One message in a model conversation. An {@code ASSISTANT} message may carry
 * {@code toolCalls} (the model asking to run tools); a {@code TOOL} message
 * carries the result of one such call, linked by {@code toolCallId}.
 */
public record ChatMessage(Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null, List.of());
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null, List.of());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null, List.of());
    }

    /** The model's turn where it requests one or more tools. */
    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, null, null, toolCalls);
    }

    public static ChatMessage tool(String content, String toolCallId) {
        return new ChatMessage(Role.TOOL, content, toolCallId, List.of());
    }
}
