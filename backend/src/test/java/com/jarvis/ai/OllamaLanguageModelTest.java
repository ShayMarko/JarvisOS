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

    @Test
    void salvagesToolCallEmittedAsPlainTextJson() {
        // Weak models emit the call as content JSON with "parameters" instead of using tool_calls.
        var r = model().parseResponse(node(
                "{\"message\":{\"content\":\"{\\\"name\\\": \\\"read_file\\\", \\\"parameters\\\": {\\\"path\\\": \\\"reminders.txt\\\"}}\"}}"));
        assertThat(r.wantsTools()).isTrue();
        assertThat(r.toolCalls().get(0).name()).isEqualTo("read_file");
        assertThat(r.toolCalls().get(0).argumentsJson()).contains("reminders.txt");
    }

    @Test
    void salvagesToolCallWrappedInCodeFence() {
        var r = model().parseResponse(node(
                "{\"message\":{\"content\":\"```json\\n{\\\"name\\\": \\\"calculate\\\", \\\"arguments\\\": {\\\"expression\\\": \\\"3*3\\\"}}\\n```\"}}"));
        assertThat(r.wantsTools()).isTrue();
        assertThat(r.toolCalls().get(0).name()).isEqualTo("calculate");
    }

    @Test
    void salvagesToolCallEmbeddedInAFenceWithTrailingProse() {
        // qwen2.5-coder behavior: shows the write_file call in a ```json fence + adds prose after it.
        var r = model().parseResponse(node(
                "{\"message\":{\"content\":\"```json\\n{\\\"name\\\": \\\"write_file\\\", \\\"arguments\\\": "
                + "{\\\"path\\\": \\\"Projects/x/pom.xml\\\", \\\"content\\\": \\\"<project/>\\\"}}\\n```\\n"
                + "You can now run the app with mvn spring-boot:run.\"}}"));
        assertThat(r.wantsTools()).isTrue();
        assertThat(r.toolCalls().get(0).name()).isEqualTo("write_file");
        assertThat(r.toolCalls().get(0).argumentsJson()).contains("Projects/x/pom.xml");
    }

    @Test
    void recoversAWriteFileWithBrokenQuoteEscaping() {
        // The model hand-writes a write_file call but doesn't escape the quotes inside the code →
        // invalid JSON. Lenient recovery should still extract path + content and write the file.
        String malformed = "{\"name\":\"write_file\",\"arguments\":{\"path\":\"a.py\","
                + "\"content\":\"print(\"hi\")\"}}";   // print("hi") quotes are NOT escaped
        var root = mapper.createObjectNode();
        root.putObject("message").put("content", malformed);
        var r = model().parseResponse(root);
        assertThat(r.wantsTools()).isTrue();
        assertThat(r.toolCalls().get(0).name()).isEqualTo("write_file");
        assertThat(r.toolCalls().get(0).argumentsJson()).contains("a.py").contains("print");
    }

    @Test
    void doesNotHijackANormalAnswerThatMentionsJson() {
        var r = model().parseResponse(node(
                "{\"message\":{\"content\":\"Here is the plan: {\\\"name\\\": \\\"x\\\"} and then we proceed.\"}}"));
        assertThat(r.wantsTools()).isFalse();  // trailing prose ⇒ treated as a normal answer
        assertThat(r.text()).contains("Here is the plan");
    }

    private com.fasterxml.jackson.databind.JsonNode node(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
