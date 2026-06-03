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
    private final int numPredict;

    public OllamaLanguageModel(JarvisAiProperties props, ObjectMapper mapper) {
        super(mapper, build(props), "Ollama");
        this.model = props.getOllamaModel();
        // Local is free — use a generous output budget so it can write whole files without truncating.
        this.numPredict = props.getOllamaNumPredict();
    }

    private static RestClient build(JarvisAiProperties props) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(3));
        // Bigger local models (e.g. qwen2.5-coder:14b) generating a multi-file build can take minutes;
        // too short a read timeout makes the call fail and silently fall back to the mock. Configurable.
        rf.setReadTimeout(Duration.ofSeconds(props.getOllamaTimeoutSeconds()));
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
        body.put("options", Map.of("num_predict", numPredict));
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
            if (salvaged.isEmpty()) {
                // The model often hand-writes a write_file call with BROKEN escaping (unescaped quotes
                // inside code content) → invalid JSON the strict salvage can't parse. Recover the file
                // by locating the content boundaries structurally and re-encoding cleanly.
                salvaged = lenientWriteFile(content);
            }
            if (!salvaged.isEmpty()) {
                return ModelResponse.tools(salvaged, in, out);
            }
        }
        return toolCalls.isEmpty()
                ? ModelResponse.text(content, in, out)
                : ModelResponse.tools(toolCalls, in, out);
    }

    /**
     * Recovers tool calls a model emitted as JSON in the content field instead of via the native
     * tool_calls channel — including when it wraps them in a ```json fence and/or surrounds them with
     * prose (e.g. qwen "showing" a write_file call). Scans the whole content for EVERY balanced {…}
     * object of the shape {@code {"name": "...", "parameters"|"arguments"|"args": {…}}} and turns each
     * into a real ToolCall; non-matching text is ignored. A bare object that isn't this shape is left
     * alone, and an unknown tool name just yields an "Unknown tool" result downstream (harmless).
     */
    private List<ToolCall> salvageToolCalls(String content) {
        List<ToolCall> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        int i = 0;
        while (i < content.length()) {
            int brace = content.indexOf('{', i);
            if (brace < 0) {
                break;
            }
            int end = matchingBrace(content, brace);
            if (end < 0) {
                break;   // unbalanced (likely truncated) — stop
            }
            String chunk = content.substring(brace, end + 1);
            try {
                JsonNode node = mapper.readTree(chunk);
                String name = node.path("name").asText("");
                JsonNode params = node.has("parameters") ? node.get("parameters")
                        : node.has("arguments") ? node.get("arguments")
                        : node.get("args");
                if (!name.isBlank() && params != null && params.isObject()) {
                    out.add(new ToolCall("call_" + out.size(), name, params.toString()));
                    i = end + 1;   // consumed this tool-call object; keep scanning for more
                    continue;
                }
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
                // not parseable as a JSON object here — fall through and advance
            }
            i = brace + 1;   // not a tool call at this position; scan from the next '{'
        }
        return out;
    }

    private static final java.util.regex.Pattern WRITE_FILE_NAME =
            java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"write_file\"");
    private static final java.util.regex.Pattern PATH_FIELD =
            java.util.regex.Pattern.compile("\"(?:path|file_path|filepath|file|filename)\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Last-resort recovery of a hand-written {@code write_file} call whose JSON is malformed because the
     * model didn't escape quotes inside the code content. Finds the path and the content's boundaries by
     * STRUCTURE (the content string's closing quote is the last {@code "} immediately before a {@code }}),
     * then re-encodes proper JSON so the tool runs. Single write_file only — the continuation loop handles
     * subsequent files. Returns empty if it doesn't clearly look like a write_file call.
     */
    private List<ToolCall> lenientWriteFile(String content) {
        List<ToolCall> out = new ArrayList<>();
        if (content == null || !WRITE_FILE_NAME.matcher(content).find()) {
            return out;
        }
        java.util.regex.Matcher pm = PATH_FIELD.matcher(content);
        if (!pm.find()) {
            return out;
        }
        String path = pm.group(1);
        int ci = content.indexOf("\"content\"");
        if (ci < 0) {
            return out;
        }
        int colon = content.indexOf(':', ci);
        int start = colon < 0 ? -1 : content.indexOf('"', colon + 1);
        if (start < 0) {
            return out;
        }
        // The content value ends at the last '"' that is followed by optional whitespace then '}'.
        int endQuote = -1;
        for (int k = content.length() - 1; k > start; k--) {
            if (content.charAt(k) == '"') {
                int j = k + 1;
                while (j < content.length() && Character.isWhitespace(content.charAt(j))) {
                    j++;
                }
                if (j < content.length() && content.charAt(j) == '}') {
                    endQuote = k;
                    break;
                }
            }
        }
        if (endQuote <= start) {
            return out;
        }
        String fileContent = unescapeJson(content.substring(start + 1, endQuote));
        try {
            out.add(new ToolCall("call_0", "write_file",
                    mapper.writeValueAsString(java.util.Map.of("path", path, "content", fileContent))));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of();
        }
        return out;
    }

    /** Decode the common JSON string escapes the model DID emit (leave stray quotes as literal text). */
    private static String unescapeJson(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
                .replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
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
