package com.jarvis.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anthropic Messages API adapter (spec §15/§16 jarvis-ai) with full tool-use:
 * it advertises the agent's tools, executes Claude's {@code tool_use} requests
 * via the runtime, and feeds {@code tool_result}s back — driving the same loop
 * the mock model does, but with real reasoning. Active when
 * {@code jarvis.ai.provider=claude} and an API key is set.
 *
 * <p>{@link #buildRequestBody} and {@link #parseResponse} are pure and unit-tested
 * (no network); only {@link #generate} performs I/O.
 */
public class AnthropicLanguageModel extends AbstractHttpLanguageModel {

    private static final String VERSION = "2023-06-01";

    private final String model;
    private final int maxTokens;

    public AnthropicLanguageModel(JarvisAiProperties props, ObjectMapper mapper) {
        super(mapper, RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", props.getAnthropicApiKey())
                .defaultHeader("anthropic-version", VERSION)
                .defaultHeader("content-type", "application/json")
                .build(), "Anthropic");
        this.model = props.getModel();
        this.maxTokens = props.getMaxTokens();
    }

    @Override
    public String name() {
        return "claude:" + model;
    }

    @Override
    protected String chatUri() {
        return "/v1/messages";
    }

    /** Maps our conversation + tools into an Anthropic Messages API request body. */
    @Override
    protected Map<String, Object> buildRequestBody(List<ChatMessage> messages, List<ToolSpec> tools) {
        StringBuilder system = new StringBuilder();
        List<Map<String, Object>> apiMessages = new ArrayList<>();
        List<Map<String, Object>> pendingToolResults = new ArrayList<>();

        for (ChatMessage m : messages) {
            switch (m.role()) {
                case SYSTEM -> system.append(m.content()).append("\n");
                case USER -> {
                    flush(pendingToolResults, apiMessages);
                    apiMessages.add(Map.of("role", "user", "content", m.content()));
                }
                case ASSISTANT -> {
                    flush(pendingToolResults, apiMessages);
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        List<Object> blocks = new ArrayList<>();
                        for (ToolCall tc : m.toolCalls()) {
                            Map<String, Object> block = new LinkedHashMap<>();
                            block.put("type", "tool_use");
                            block.put("id", tc.id());
                            block.put("name", tc.name());
                            block.put("input", toObject(tc.argumentsJson()));
                            blocks.add(block);
                        }
                        apiMessages.add(Map.of("role", "assistant", "content", blocks));
                    } else {
                        apiMessages.add(Map.of("role", "assistant", "content", m.content()));
                    }
                }
                case TOOL -> {
                    Map<String, Object> block = new LinkedHashMap<>();
                    block.put("type", "tool_result");
                    block.put("tool_use_id", m.toolCallId());
                    block.put("content", m.content());
                    pendingToolResults.add(block);
                }
            }
        }
        flush(pendingToolResults, apiMessages);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        // System prompt as a cached block — persona + agent prompt repeat across every
        // tool-loop step and across a session, so caching it cuts repeated input cost.
        String sys = system.toString().trim();
        body.put("system", List.of(Map.of(
                "type", "text", "text", sys, "cache_control", Map.of("type", "ephemeral"))));
        body.put("messages", apiMessages);
        if (tools != null && !tools.isEmpty()) {
            List<Object> toolDefs = new ArrayList<>();
            for (int i = 0; i < tools.size(); i++) {
                ToolSpec t = tools.get(i);
                Map<String, Object> def = new LinkedHashMap<>();
                def.put("name", t.name());
                def.put("description", t.description());
                def.put("input_schema", toObject(t.parametersSchema()));
                // Mark the LAST tool as a cache breakpoint → the whole tools array is cached.
                if (i == tools.size() - 1) {
                    def.put("cache_control", Map.of("type", "ephemeral"));
                }
                toolDefs.add(def);
            }
            body.put("tools", toolDefs);
        }
        return body;
    }

    /** Parses an Anthropic Messages API response into our {@link ModelResponse}. */
    @Override
    protected ModelResponse parseResponse(JsonNode root) {
        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                text.append(block.path("text").asText());
            } else if ("tool_use".equals(type)) {
                toolCalls.add(new ToolCall(
                        block.path("id").asText(),
                        block.path("name").asText(),
                        block.path("input").toString()));
            }
        }
        int in = root.path("usage").path("input_tokens").asInt(0);
        int out = root.path("usage").path("output_tokens").asInt(0);
        return toolCalls.isEmpty()
                ? ModelResponse.text(text.toString(), in, out)
                : ModelResponse.tools(toolCalls, in, out);
    }

    private void flush(List<Map<String, Object>> pending, List<Map<String, Object>> apiMessages) {
        if (!pending.isEmpty()) {
            apiMessages.add(Map.of("role", "user", "content", new ArrayList<>(pending)));
            pending.clear();
        }
    }
}
