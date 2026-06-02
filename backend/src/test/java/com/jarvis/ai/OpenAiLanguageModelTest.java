package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class OpenAiLanguageModelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private OpenAiLanguageModel model() {
        JarvisAiProperties props = new JarvisAiProperties();
        props.setOpenaiModel("gpt-4o-mini");
        props.setOpenaiApiKey("sk-test");
        return new OpenAiLanguageModel(props, mapper);
    }

    @Test
    void buildsChatCompletionsBodyWithRealToolSchema() throws Exception {
        ToolSpec spec = new ToolSpec("web_search", "Search the web",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}");
        Map<String, Object> body = model().buildRequestBody(List.of(ChatMessage.user("hi")), List.of(spec));

        assertThat(body.get("model")).isEqualTo("gpt-4o-mini");
        assertThat(body.get("tool_choice")).isEqualTo("auto");
        String json = mapper.writeValueAsString(body);
        assertThat(json).contains("\"tools\"").contains("\"query\"").contains("\"type\":\"object\"");
        assertThat(json).doesNotContain("nodeType").doesNotContain("bigDecimal");
    }

    @Test
    void tutorialMessageWithToolCallAndResultRoundTrips() throws Exception {
        // assistant tool-call + tool result must carry ids OpenAI requires
        List<ChatMessage> msgs = List.of(
                ChatMessage.assistantToolCalls(List.of(new ToolCall("call_1", "web_search", "{\"query\":\"x\"}"))),
                ChatMessage.tool("results...", "call_1"));
        String json = mapper.writeValueAsString(model().buildRequestBody(msgs, List.of()));
        assertThat(json).contains("\"tool_calls\"").contains("\"id\":\"call_1\"").contains("\"tool_call_id\":\"call_1\"");
    }

    @Test
    void parsesToolCallAndPlainAnswer() {
        var withTool = model().parseResponse(node(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"call_9\",\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"hi\\\"}\"}}]}}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":7}}"));
        assertThat(withTool.wantsTools()).isTrue();
        assertThat(withTool.toolCalls().get(0).id()).isEqualTo("call_9");
        assertThat(withTool.toolCalls().get(0).argumentsJson()).contains("hi");
        assertThat(withTool.promptTokens()).isEqualTo(5);

        var plain = model().parseResponse(node(
                "{\"choices\":[{\"message\":{\"content\":\"hello there\"}}],\"usage\":{}}"));
        assertThat(plain.wantsTools()).isFalse();
        assertThat(plain.text()).isEqualTo("hello there");
    }

    private JsonNode node(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
