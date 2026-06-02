package com.jarvis.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Local Ollama adapter (real reasoning, no API key, fully offline) using the
 * OpenAI-compatible {@code /api/chat} tool-calling format. Drives the same agent
 * loop as the Anthropic/mock adapters. Active when {@code jarvis.ai.provider=ollama}
 * and a model is pulled (e.g. {@code ollama pull llama3.1}).
 *
 * <p>{@link #buildRequestBody} and {@link #parseResponse} are pure (no network);
 * only {@link #generate} performs I/O.
 */
public class OllamaLanguageModel implements LanguageModel {

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;
    private final int maxTokens;

    public OllamaLanguageModel(JarvisAiProperties props, ObjectMapper mapper) {
        this.mapper = mapper;
        this.model = props.getOllamaModel();
        this.maxTokens = props.getMaxTokens();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(3));
        rf.setReadTimeout(Duration.ofSeconds(120)); // local generation can be slow
        this.client = RestClient.builder()
                .requestFactory(rf)
                .baseUrl(props.getOllamaBaseUrl())
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public String name() {
        return "ollama:" + model;
    }

    @Override
    public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools) {
        return generate(messages, tools, null);
    }

    @Override
    public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools, String modelOverride) {
        try {
            Map<String, Object> body = buildRequestBody(messages, tools);
            if (modelOverride != null && !modelOverride.isBlank()) {
                body.put("model", modelOverride);
            }
            String raw = client.post().uri("/api/chat").body(body).retrieve().body(String.class);
            return parseResponse(mapper.readTree(raw));
        } catch (Exception e) {
            throw new IllegalStateException("Ollama request failed: " + e.getMessage(), e);
        }
    }

    /** Maps our conversation + tools into an Ollama /api/chat request body. */
    Map<String, Object> buildRequestBody(List<ChatMessage> messages, List<ToolSpec> tools) {
        List<Map<String, Object>> apiMessages = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            switch (m.role()) {
                case SYSTEM -> { msg.put("role", "system"); msg.put("content", m.content()); }
                case USER -> { msg.put("role", "user"); msg.put("content", m.content()); }
                case TOOL -> { msg.put("role", "tool"); msg.put("content", m.content() == null ? "" : m.content()); }
                case ASSISTANT -> {
                    msg.put("role", "assistant");
                    msg.put("content", m.content() == null ? "" : m.content());
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        List<Object> calls = new ArrayList<>();
                        for (ToolCall tc : m.toolCalls()) {
                            calls.add(Map.of("type", "function",
                                    "function", Map.of("name", tc.name(), "arguments", toObject(tc.argumentsJson()))));
                        }
                        msg.put("tool_calls", calls);
                    }
                }
            }
            apiMessages.add(msg);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", apiMessages);
        body.put("stream", false);
        body.put("options", Map.of("num_predict", maxTokens));
        if (tools != null && !tools.isEmpty()) {
            List<Object> toolDefs = new ArrayList<>();
            for (ToolSpec t : tools) {
                toolDefs.add(Map.of("type", "function", "function", Map.of(
                        "name", t.name(), "description", t.description(), "parameters", toObject(t.parametersSchema()))));
            }
            body.put("tools", toolDefs);
        }
        return body;
    }

    /** Parses an Ollama /api/chat response into our {@link ModelResponse}. */
    ModelResponse parseResponse(JsonNode root) {
        JsonNode message = root.path("message");
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode calls = message.path("tool_calls");
        int i = 0;
        for (JsonNode call : calls) {
            JsonNode fn = call.path("function");
            JsonNode argsNode = fn.path("arguments");
            // Ollama may return arguments as a JSON object or as a JSON-encoded string.
            String args = argsNode.isTextual() ? argsNode.asText() : argsNode.toString();
            toolCalls.add(new ToolCall("call_" + (i++), fn.path("name").asText(), args));
        }
        int in = root.path("prompt_eval_count").asInt(0);
        int out = root.path("eval_count").asInt(0);
        return toolCalls.isEmpty()
                ? ModelResponse.text(message.path("content").asText(""), in, out)
                : ModelResponse.tools(toolCalls, in, out);
    }

    /**
     * Parse a JSON string into a plain Object tree (Map/List/scalars) so it serializes
     * unambiguously inside the request body. Returning a {@link JsonNode} here risks it
     * being bean-serialized (array/bigDecimal/nodeType…) into the outgoing JSON, which
     * hands the model a garbage tool schema.
     */
    private Object toObject(String json) {
        try {
            return mapper.readValue(json == null || json.isBlank() ? "{}" : json, Object.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
