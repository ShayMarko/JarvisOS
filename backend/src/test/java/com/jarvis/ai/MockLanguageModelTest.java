package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MockLanguageModelTest {

    private final MockLanguageModel model = new MockLanguageModel();
    private final List<ToolSpec> tools = List.of(
            new ToolSpec("list_files", "", "{}"),
            new ToolSpec("system_status", "", "{}"));

    @Test
    void requestsAToolForAFileQuery() {
        ModelResponse r = model.generate(List.of(ChatMessage.user("list my files please")), tools);
        assertThat(r.wantsTools()).isTrue();
        assertThat(r.toolCalls().get(0).name()).isEqualTo("list_files");
    }

    @Test
    void composesAnswerAfterToolResult() {
        ModelResponse r = model.generate(List.of(
                ChatMessage.user("list my files"),
                ChatMessage.assistant("(calling list_files)"),
                ChatMessage.tool("📁 Notes\n📄 todo.txt", "call_1")), tools);
        assertThat(r.wantsTools()).isFalse();
        assertThat(r.text()).contains("todo.txt");
    }

    @Test
    void answersDirectlyWhenNoToolMatches() {
        ModelResponse r = model.generate(List.of(ChatMessage.user("hello there")), tools);
        assertThat(r.wantsTools()).isFalse();
        assertThat(r.text()).isNotBlank();
    }
}
