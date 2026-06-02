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
public class OllamaLanguageModel extends AbstractHttpLanguageModel {

    private final String model;
    private final int maxTokens;

    public OllamaLanguageModel(JarvisAiProperties props, ObjectMapper mapper) {
        super(mapper, build(props), "Ollama");
        this.model = props.getOllamaModel();
        this.maxTokens = props.getMaxTokens();
    }

    private static RestClient build(JarvisAiProperties props) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(3));
        rf.setReadTimeout(Duration.ofSeconds(120)); // local generation can be slow
        return RestClient.builder()
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
    protected String chatUri() {
        return "/api/chat";
    }

    /** Maps our conversation + tools into an Ollama /api/chat request body. */
    @Override
    protected Map<String, Object> buildRequestBody(List<ChatMessage> messages, List<ToolSpec> tools) {
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
    @Override
    protected ModelResponse parseResponse(JsonNode root) {
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
        String content = message.path("content").asText("");
        if (toolCalls.isEmpty()) {
            // Smaller local models (e.g. llama3.2:3b) often ignore the native tool_calls channel and
            // instead emit the call as raw JSON in the content — e.g. {"name":"read_file","parameters":{…}}.
            // Without this, that JSON leaks to the user as the "answer". Salvage it into a real ToolCall
            // so the agent loop actually runs the tool instead of printing the JSON.
            List<ToolCall> salvaged = salvageToolCalls(content);
            if (!salvaged.isEmpty()) {
                return ModelResponse.tools(salvaged, in, out);
            }
        }
        return toolCalls.isEmpty()
                ? ModelResponse.text(content, in, out)
                : ModelResponse.tools(toolCalls, in, out);
    }

    /**
     * Recovers tool calls a weak model emitted as plain-text JSON in the content field instead of via
     * the native tool_calls channel. Only triggers when the whole message is one (or a few stacked)
     * JSON objects of the shape {@code {"name": "...", "parameters"|"arguments"|"args": {…}}} — a strong
     * signal it's a tool call, not prose — so a normal answer that merely mentions JSON is never hijacked.
     */
    private List<ToolCall> salvageToolCalls(String content) {
        List<ToolCall> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        String s = content.strip();
        // Strip a ```json … ``` (or bare ```) fence if the model wrapped the call.
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
            s = s.strip();
        }
        if (!s.startsWith("{")) {
            return out;
        }
        int i = 0;
        // Walk one or more concatenated top-level {…} objects (some models stack two calls back-to-back).
        while (i < s.length() && s.charAt(i) == '{') {
            int end = matchingBrace(s, i);
            if (end < 0) {
                break;
            }
            String chunk = s.substring(i, end + 1);
            try {
                JsonNode node = mapper.readTree(chunk);
                String name = node.path("name").asText("");
                JsonNode params = node.has("parameters") ? node.get("parameters")
                        : node.has("arguments") ? node.get("arguments")
                        : node.get("args");
                if (!name.isBlank() && params != null && params.isObject()) {
                    out.add(new ToolCall("call_" + out.size(), name, params.toString()));
                } else {
                    return List.of(); // not the tool-call shape — treat the whole content as an answer
                }
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
                return List.of();
            }
            i = end + 1;
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
        // Only accept if we consumed the entire message (no trailing prose after the JSON).
        return i >= s.length() ? out : List.of();
    }

    /** Index of the brace that closes the object opening at {@code open}, respecting strings/escapes. */
    private static int matchingBrace(String s, int open) {
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (esc) { esc = false; }
                else if (c == '\\') { esc = true; }
                else if (c == '"') { inStr = false; }
            } else if (c == '"') {
                inStr = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
