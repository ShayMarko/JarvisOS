package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class OllamaLanguageModelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private OllamaLanguageModel model() {
        JarvisAiProperties props = new JarvisAiProperties();
        props.setOllamaModel("llama3.2:3b");
        return new OllamaLanguageModel(props, mapper);
    }

    @Test
    void toolSchemaSerializesAsRealJsonNotBeanDump() throws Exception {
        ToolSpec spec = new ToolSpec("web_search", "Search the web",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
        Map<String, Object> body = model().buildRequestBody(
                List.of(ChatMessage.user("whats app")), List.of(spec));

        // Serialize exactly as the HTTP client would; the schema must be a real JSON schema,
        // NOT a Jackson JsonNode bean dump (array/bigDecimal/nodeType/...).
        String json = mapper.writeValueAsString(body);
        assertThat(json).contains("\"parameters\"").contains("\"query\"").contains("\"type\":\"object\"");
        assertThat(json).doesNotContain("nodeType").doesNotContain("bigDecimal").doesNotContain("valueNode");
    }

    @Test
    void parsesObjectAndStringToolArguments() {
        // arguments as an object
        var objResp = model().parseResponse(node(
                "{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"web_search\",\"arguments\":{\"query\":\"hi\"}}}]}}"));
        assertThat(objResp.wantsTools()).isTrue();
        assertThat(objResp.toolCalls().get(0).argumentsJson()).contains("\"query\"").contains("hi");

        // arguments as a JSON-encoded string
        var strResp = model().parseResponse(node(
                "{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"hi\\\"}\"}}]}}"));
        assertThat(strResp.toolCalls().get(0).argumentsJson()).contains("\"query\"").contains("hi");
    }

    private com.fasterxml.jackson.databind.JsonNode node(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
