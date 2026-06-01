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
public class AnthropicLanguageModel implements LanguageModel {

    private static final String VERSION = "2023-06-01";

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;
    private final int maxTokens;

    public AnthropicLanguageModel(JarvisAiProperties props, ObjectMapper mapper) {
        this.mapper = mapper;
        this.model = props.getModel();
        this.maxTokens = props.getMaxTokens();
        this.client = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", props.getAnthropicApiKey())
                .defaultHeader("anthropic-version", VERSION)
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public String name() {
        return "claude:" + model;
    }

    @Override
    public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools) {
        try {
            Map<String, Object> body = buildRequestBody(messages, tools);
            String raw = client.post().uri("/v1/messages").body(body).retrieve().body(String.class);
            return parseResponse(mapper.readTree(raw));
        } catch (Exception e) {
            throw new IllegalStateException("Anthropic request failed: " + e.getMessage(), e);
        }
    }

    /** Maps our conversation + tools into an Anthropic Messages API request body. */
    Map<String, Object> buildRequestBody(List<ChatMessage> messages, List<ToolSpec> tools) {
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
                            block.put("input", toJson(tc.argumentsJson()));
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
        body.put("system", system.toString().trim());
        body.put("messages", apiMessages);
        if (tools != null && !tools.isEmpty()) {
            List<Object> toolDefs = new ArrayList<>();
            for (ToolSpec t : tools) {
                Map<String, Object> def = new LinkedHashMap<>();
                def.put("name", t.name());
                def.put("description", t.description());
                def.put("input_schema", toJson(t.parametersSchema()));
                toolDefs.add(def);
            }
            body.put("tools", toolDefs);
        }
        return body;
    }

    /** Parses an Anthropic Messages API response into our {@link ModelResponse}. */
    ModelResponse parseResponse(JsonNode root) {
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

    private JsonNode toJson(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
