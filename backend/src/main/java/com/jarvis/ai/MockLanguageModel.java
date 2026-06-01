package com.jarvis.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An offline, deterministic {@link LanguageModel} so the entire Brain + agent +
 * tool-calling loop runs and is testable with no API key. It is not an LLM — it
 * uses simple heuristics to decide whether to call a tool, then composes a plain
 * answer from the tool result. Swap in {@link AnthropicLanguageModel} (set
 * {@code jarvis.ai.provider=claude} + an API key) for real reasoning.
 */
public class MockLanguageModel implements LanguageModel {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools) {
        int prompt = estimateTokens(messages);

        // If a tool already ran this turn, compose a final answer from its result.
        Optional<ChatMessage> toolResult = lastOfRole(messages, ChatMessage.Role.TOOL);
        if (toolResult.isPresent()) {
            String answer = "Here's what I found:\n\n" + toolResult.get().content();
            return ModelResponse.text(answer, prompt, estimate(answer));
        }

        String userMsg = lastOfRole(messages, ChatMessage.Role.USER).map(ChatMessage::content).orElse("");
        Set<String> available = toolNames(tools);

        Optional<ToolCall> call = chooseTool(userMsg, available);
        if (call.isPresent()) {
            return ModelResponse.tools(List.of(call.get()), prompt, 8);
        }

        String reply = "I'm the local Jarvis brain (mock model — no API key set). I understood: \""
                + userMsg + "\". I can act on files, search, system status and memory; "
                + "connect a real model (set jarvis.ai.provider=claude) for full reasoning.";
        return ModelResponse.text(reply, prompt, estimate(reply));
    }

    private Optional<ToolCall> chooseTool(String message, Set<String> available) {
        String m = message.toLowerCase();
        // Agentic memory: when the user tells Jarvis to remember something, save it.
        if (has(available, "memory_write")
                && (m.startsWith("remember") || matches(m, "note that", "don't forget", "make a note", "keep in mind"))) {
            String content = message.replaceAll("(?i)^(please\\s+)?(remember|note)\\s+(that\\s+)?", "").trim();
            String title = content.length() > 40 ? content.substring(0, 40) : content;
            return Optional.of(new ToolCall("call_1", "memory_write",
                    "{\"title\":\"" + jsonEscape(title) + "\",\"content\":\"" + jsonEscape(content)
                    + "\",\"category\":\"preference\"}"));
        }
        if (has(available, "system_status") && matches(m, "status", "cpu", "ram", "memory usage", "resource", "system")) {
            return Optional.of(new ToolCall("call_1", "system_status", "{}"));
        }
        if (has(available, "read_file") && matches(m, "read", "open", "contents of")) {
            String path = firstPathLike(message);
            if (path != null) {
                return Optional.of(new ToolCall("call_1", "read_file", "{\"path\":\"" + path + "\"}"));
            }
        }
        if (has(available, "web_search")
                && matches(m, "on the web", "web search", "search the web", "latest", "look up online", "google")) {
            return Optional.of(new ToolCall("call_1", "web_search",
                    "{\"query\":\"" + jsonEscape(message.replaceAll("[\"',?]", "")) + "\"}"));
        }
        if (has(available, "kb_search")
                && matches(m, "document", "docs", "according to", "what does my", "knowledge", "my notes", "my plan")) {
            return Optional.of(new ToolCall("call_1", "kb_search",
                    "{\"query\":\"" + jsonEscape(message.replaceAll("[\"',?]", "")) + "\"}"));
        }
        if (has(available, "search_files") && matches(m, "search", "find", "look for")) {
            return Optional.of(new ToolCall("call_1", "search_files",
                    "{\"query\":\"" + jsonEscape(keyword(message)) + "\"}"));
        }
        if (has(available, "memory_search") && matches(m, "remember", "memory", "know about me", "preference")) {
            return Optional.of(new ToolCall("call_1", "memory_search", "{\"query\":\"\"}"));
        }
        if (has(available, "list_files") && matches(m, "file", "files", "directory", "folder", "explorer", "list")) {
            return Optional.of(new ToolCall("call_1", "list_files", "{\"path\":\"\"}"));
        }
        return Optional.empty();
    }

    private static boolean matches(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static boolean has(Set<String> available, String tool) {
        return available.contains(tool);
    }

    private static String firstPathLike(String message) {
        return Arrays.stream(message.split("\\s+"))
                .map(w -> w.replaceAll("[\"',]", ""))
                .filter(w -> w.contains(".") || w.contains("/"))
                .findFirst().orElse(null);
    }

    /** A naive "query" = the words after a search keyword, else the whole message. */
    private static String keyword(String message) {
        String[] words = message.split("\\s+");
        for (int i = 0; i < words.length - 1; i++) {
            String w = words[i].toLowerCase();
            if (w.equals("search") || w.equals("find") || w.equals("for")) {
                return words[i + 1].replaceAll("[\"',?.]", "");
            }
        }
        return message.replaceAll("[\"',?.]", "").trim();
    }

    private static Set<String> toolNames(List<ToolSpec> tools) {
        return tools == null ? Set.of() : tools.stream().map(ToolSpec::name).collect(java.util.stream.Collectors.toSet());
    }

    private static Optional<ChatMessage> lastOfRole(List<ChatMessage> messages, ChatMessage.Role role) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == role) {
                return Optional.of(messages.get(i));
            }
        }
        return Optional.empty();
    }

    private static int estimateTokens(List<ChatMessage> messages) {
        return messages.stream().mapToInt(msg -> estimate(msg.content())).sum();
    }

    private static int estimate(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
