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
 * OpenAI adapter using the Chat Completions API ({@code POST /v1/chat/completions})
 * with function/tool calling. Drives the same agent loop as the Anthropic/Ollama/mock
 * adapters. Active when {@code jarvis.ai.provider=openai} and an API key is set.
 *
 * <p>{@link #buildRequestBody} and {@link #parseResponse} are pure (no network);
 * only {@link #generate} performs I/O. Tool schemas/arguments are passed as plain
 * Map/List trees (never raw {@code JsonNode}) so they serialise correctly.
 */
public class OpenAiLanguageModel extends AbstractHttpLanguageModel {

    private final String model;
    private final int maxTokens;

    public OpenAiLanguageModel(JarvisAiProperties props, ObjectMapper mapper) {
        super(mapper, build(props), "OpenAI");
        this.model = props.getOpenaiModel();
        this.maxTokens = props.getMaxTokens();
    }

    private static RestClient build(JarvisAiProperties props) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .requestFactory(rf)
                .baseUrl(props.getOpenaiBaseUrl())
                .defaultHeader("content-type", "application/json")
                .defaultHeader("authorization", "Bearer " + props.getOpenaiApiKey())
                .build();
    }

    @Override
    public String name() {
        return "openai:" + model;
    }

    @Override
    protected String chatUri() {
        return "/chat/completions";
    }

    /** Maps our conversation + tools into an OpenAI /chat/completions request body. */
    @Override
    protected Map<String, Object> buildRequestBody(List<ChatMessage> messages, List<ToolSpec> tools) {
        List<Map<String, Object>> apiMessages = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            switch (m.role()) {
                case SYSTEM -> { msg.put("role", "system"); msg.put("content", m.content()); }
                case USER -> { msg.put("role", "user"); msg.put("content", m.content()); }
                case TOOL -> {
                    msg.put("role", "tool");
                    msg.put("tool_call_id", m.toolCallId());
                    msg.put("content", m.content() == null ? "" : m.content());
                }
                case ASSISTANT -> {
                    msg.put("role", "assistant");
                    msg.put("content", m.content());   // may be null when only tool calls
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        List<Object> calls = new ArrayList<>();
                        for (ToolCall tc : m.toolCalls()) {
                            calls.add(Map.of("id", tc.id(), "type", "function",
                                    "function", Map.of("name", tc.name(), "arguments",
                                            tc.argumentsJson() == null ? "{}" : tc.argumentsJson())));
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
        body.put("max_tokens", maxTokens);
        // OpenAI auto-caches identical request prefixes; a stable key per build keeps the
        // system+tools prefix routed to the same cache. (System+tools come first above.)
        body.put("prompt_cache_key", "jarvis-" + model);
        if (tools != null && !tools.isEmpty()) {
            List<Object> toolDefs = new ArrayList<>();
            for (ToolSpec t : tools) {
                toolDefs.add(Map.of("type", "function", "function", Map.of(
                        "name", t.name(), "description", t.description(), "parameters", toObject(t.parametersSchema()))));
            }
            body.put("tools", toolDefs);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    /** Parses an OpenAI /chat/completions response into our {@link ModelResponse}. */
    @Override
    protected ModelResponse parseResponse(JsonNode root) {
        JsonNode message = root.path("choices").path(0).path("message");
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode fn = call.path("function");
            JsonNode args = fn.path("arguments");
            String argsJson = args.isTextual() ? args.asText() : args.toString();
            toolCalls.add(new ToolCall(call.path("id").asText("call_" + toolCalls.size()),
                    fn.path("name").asText(), argsJson));
        }
        int in = root.path("usage").path("prompt_tokens").asInt(0);
        int out = root.path("usage").path("completion_tokens").asInt(0);
        return toolCalls.isEmpty()
                ? ModelResponse.text(message.path("content").asText(""), in, out)
                : ModelResponse.tools(toolCalls, in, out);
    }
}
