package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies the real Anthropic mapping without any network: the request body
 * (system/messages/tool_use/tool_result/tools) and the response parsing.
 */
class AnthropicLanguageModelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnthropicLanguageModel model = new AnthropicLanguageModel(new JarvisAiProperties(), mapper);

    @Test
    @SuppressWarnings("unchecked")
    void buildsRequestWithSystemMessagesToolUseAndToolResult() {
        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are Jarvis."),
                ChatMessage.user("list my files"),
                ChatMessage.assistantToolCalls(List.of(new ToolCall("t1", "list_files", "{\"path\":\"\"}"))),
                ChatMessage.tool("📁 Notes", "t1"));
        List<ToolSpec> tools = List.of(
                new ToolSpec("list_files", "List files.", "{\"type\":\"object\",\"properties\":{}}"));

        Map<String, Object> body = model.buildRequestBody(messages, tools);

        assertThat(body.get("system")).isEqualTo("You are Jarvis.");

        List<Map<String, Object>> msgs = (List<Map<String, Object>>) body.get("messages");
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0)).containsEntry("role", "user").containsEntry("content", "list my files");

        // assistant tool_use turn
        Map<String, Object> assistant = msgs.get(1);
        assertThat(assistant).containsEntry("role", "assistant");
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) assistant.get("content");
        assertThat(blocks.get(0)).containsEntry("type", "tool_use")
                .containsEntry("id", "t1").containsEntry("name", "list_files");

        // tool_result folded into a following user message
        Map<String, Object> toolResultMsg = msgs.get(2);
        assertThat(toolResultMsg).containsEntry("role", "user");
        List<Map<String, Object>> resultBlocks = (List<Map<String, Object>>) toolResultMsg.get("content");
        assertThat(resultBlocks.get(0)).containsEntry("type", "tool_result")
                .containsEntry("tool_use_id", "t1").containsEntry("content", "📁 Notes");

        // tools advertised with a JSON-schema input_schema
        List<Map<String, Object>> toolDefs = (List<Map<String, Object>>) body.get("tools");
        assertThat(toolDefs.get(0)).containsEntry("name", "list_files");
        assertThat(toolDefs.get(0).get("input_schema")).isInstanceOf(JsonNode.class);
    }

    @Test
    void parsesToolUseResponse() throws Exception {
        JsonNode root = mapper.readTree(
                "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"list_files\",\"input\":{\"path\":\"\"}}],"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}");
        ModelResponse r = model.parseResponse(root);
        assertThat(r.wantsTools()).isTrue();
        assertThat(r.toolCalls().get(0).name()).isEqualTo("list_files");
        assertThat(r.promptTokens()).isEqualTo(10);
    }

    @Test
    void parsesTextResponse() throws Exception {
        JsonNode root = mapper.readTree(
                "{\"content\":[{\"type\":\"text\",\"text\":\"Hello!\"}],\"usage\":{\"input_tokens\":3,\"output_tokens\":2}}");
        ModelResponse r = model.parseResponse(root);
        assertThat(r.wantsTools()).isFalse();
        assertThat(r.text()).isEqualTo("Hello!");
    }
}
