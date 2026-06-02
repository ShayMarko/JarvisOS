package com.jarvis.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared skeleton for the HTTP model adapters (Anthropic, OpenAI, Ollama). Captures the
 * identical {@code generate()} template — build body → (optional model override) → POST →
 * parse → wrap errors — and the {@link #toObject(String)} JSON helper, so each concrete
 * adapter only supplies its provider-specific request body, response parsing, chat URI,
 * and {@link #name()}. Keeps the model-call boundary uniform and makes adding a provider
 * a matter of three small methods.
 *
 * <p>{@code buildRequestBody}/{@code parseResponse} stay pure (no network) for unit testing;
 * only {@link #generate} performs I/O.
 */
abstract class AbstractHttpLanguageModel implements LanguageModel {

    protected final ObjectMapper mapper;
    private final RestClient client;
    private final String label;   // provider name used in error messages

    protected AbstractHttpLanguageModel(ObjectMapper mapper, RestClient client, String label) {
        this.mapper = mapper;
        this.client = client;
        this.label = label;
    }

    /** The provider's chat endpoint path, relative to the client's base URL. */
    protected abstract String chatUri();

    /** Provider-specific request body (pure; unit-tested). */
    protected abstract Map<String, Object> buildRequestBody(List<ChatMessage> messages, List<ToolSpec> tools);

    /** Provider-specific response parsing (pure; unit-tested). */
    protected abstract ModelResponse parseResponse(JsonNode root);

    @Override
    public final ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools) {
        return generate(messages, tools, null);
    }

    @Override
    public final ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools, String modelOverride) {
        try {
            Map<String, Object> body = buildRequestBody(messages, tools);
            if (modelOverride != null && !modelOverride.isBlank()) {
                body.put("model", modelOverride);   // route this call to a different model (e.g. cheap planner)
            }
            String raw = client.post().uri(chatUri()).body(body).retrieve().body(String.class);
            return parseResponse(mapper.readTree(raw));
        } catch (Exception e) {
            throw new IllegalStateException(label + " request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a JSON string into a plain Object tree (Map/List/scalars) so it serialises
     * unambiguously inside the request body — never a bean-dumped {@code JsonNode}.
     */
    protected Object toObject(String json) {
        try {
            return mapper.readValue(json == null || json.isBlank() ? "{}" : json, Object.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
