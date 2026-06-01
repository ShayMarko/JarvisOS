package com.jarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.MockLanguageModel;
import com.jarvis.ai.ToolSpec;
import com.jarvis.ai.tools.Tool;
import com.jarvis.ai.tools.ToolRegistry;

class AgentRuntimeTest {

    /** A fake tool standing in for a real capability. */
    private static class FakeListFiles implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("list_files", "List files.", "{}");
        }

        @Override
        public String execute(String argumentsJson) {
            return "📁 Notes\n📄 report.md";
        }
    }

    @Test
    void runsTheToolLoopAndComposesAnAnswer() {
        ToolRegistry registry = new ToolRegistry(List.of(new FakeListFiles()));
        AgentRuntime runtime = new AgentRuntime(new MockLanguageModel(), registry, new JarvisAiProperties());

        AgentDefinition agent = new AgentDefinition("File Agent", "files", "files",
                "You are the File Agent.", List.of("list_files"), "files");

        AgentRun run = runtime.run(agent, "please list my files", "");

        assertThat(run.answer()).contains("report.md");          // tool output made it into the answer
        assertThat(run.steps()).anyMatch(s -> s.kind().equals("tool"));
        assertThat(run.steps()).anyMatch(s -> s.kind().equals("answer"));
        assertThat(run.tokens()).isGreaterThan(0);
        assertThat(run.model()).isEqualTo("mock");
    }
}
