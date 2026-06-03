package com.jarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.JarvisPersonaProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.MockLanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.ai.ToolCall;
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

    /** Read-only tool (no side effect). */
    private static class FakeSearch implements Tool {
        @Override public ToolSpec spec() { return new ToolSpec("search_files", "Search.", "{}"); }
        @Override public String execute(String a) { return "no matches"; }
    }

    /** Side-effecting tool whose write here FAILS (returns an error result). */
    private static class FailingWrite implements Tool {
        @Override public ToolSpec spec() { return new ToolSpec("write_file", "Write.", "{}"); }
        @Override public boolean mutates() { return true; }
        @Override public String execute(String a) { return "Error: no file path provided."; }
    }

    /** Side-effecting tool that SUCCEEDS. */
    private static class OkWrite implements Tool {
        @Override public ToolSpec spec() { return new ToolSpec("write_file", "Write.", "{}"); }
        @Override public boolean mutates() { return true; }
        @Override public String execute(String a) { return "Wrote Projects/foo/App.java"; }
    }

    /** Scripted model: returns the queued responses in order (tool call(s) then a final answer). */
    private static class ScriptedModel implements LanguageModel {
        private final Deque<ModelResponse> script = new ArrayDeque<>();
        ScriptedModel(ModelResponse... responses) { for (ModelResponse r : responses) script.add(r); }
        @Override public ModelResponse generate(java.util.List<ChatMessage> m, java.util.List<ToolSpec> t) {
            return script.isEmpty() ? ModelResponse.text("done", 1, 1) : script.poll();
        }
        @Override public String name() { return "scripted"; }
    }

    private static AgentDefinition codeAgent(java.util.List<String> tools) {
        return new AgentDefinition("Code Agent", "code", "code", "You build things.", tools, "dev");
    }

    @Test
    void runsTheToolLoopAndComposesAnAnswer() {
        ToolRegistry registry = new ToolRegistry(List.of(new FakeListFiles()));
        AgentRuntime runtime = new AgentRuntime(new MockLanguageModel(), registry,
                new JarvisAiProperties(), new JarvisPersonaProperties());

        AgentDefinition agent = new AgentDefinition("File Agent", "files", "files",
                "You are the File Agent.", List.of("list_files"), "files");

        AgentRun run = runtime.run(agent, "please list my files", "");

        assertThat(run.answer()).contains("report.md");          // tool output made it into the answer
        assertThat(run.steps()).anyMatch(s -> s.kind().equals("tool"));
        assertThat(run.steps()).anyMatch(s -> s.kind().equals("answer"));
        assertThat(run.tokens()).isGreaterThan(0);
        assertThat(run.model()).isEqualTo("mock");
    }

    private static ToolCall call(String name) { return new ToolCall("c1", name, "{}"); }

    @Test
    void honestyGuardCorrectsAFabricatedSuccess() {
        // Agent CAN write files, but this turn it only searched (no mutation) yet claims it built the app.
        ToolRegistry registry = new ToolRegistry(List.of(new FakeSearch(), new OkWrite()));
        LanguageModel model = new ScriptedModel(
                ModelResponse.tools(List.of(call("search_files")), 5, 5),
                ModelResponse.text("I've created the full-stack app at Projects/reminders.", 5, 5));
        AgentRuntime runtime = new AgentRuntime(model, registry, new JarvisAiProperties(), new JarvisPersonaProperties());

        AgentRun run = runtime.run(codeAgent(List.of("search_files", "write_file")), "build an app", "");

        // No write succeeded → an honest banner is prepended (the original is kept, but clearly
        // re-framed as a plan rather than a confirmed result).
        assertThat(run.answer()).startsWith("⚠️");
        assertThat(run.answer()).contains("didn't actually complete");
    }

    @Test
    void noGuardWhenAMutationActuallySucceeded() {
        ToolRegistry registry = new ToolRegistry(List.of(new OkWrite()));
        LanguageModel model = new ScriptedModel(
                ModelResponse.tools(List.of(call("write_file")), 5, 5),
                ModelResponse.text("I've created the app at Projects/foo.", 5, 5));
        AgentRuntime runtime = new AgentRuntime(model, registry, new JarvisAiProperties(), new JarvisPersonaProperties());

        AgentRun run = runtime.run(codeAgent(List.of("write_file")), "build an app", "");

        assertThat(run.answer()).isEqualTo("I've created the app at Projects/foo.");   // real success, untouched
    }

    @Test
    void noGuardWhenNoCompletionWasClaimed() {
        ToolRegistry registry = new ToolRegistry(List.of(new FakeSearch(), new OkWrite()));
        LanguageModel model = new ScriptedModel(
                ModelResponse.tools(List.of(call("search_files")), 5, 5),
                ModelResponse.text("Here's a plan for the app; shall I proceed?", 5, 5));
        AgentRuntime runtime = new AgentRuntime(model, registry, new JarvisAiProperties(), new JarvisPersonaProperties());

        AgentRun run = runtime.run(codeAgent(List.of("search_files", "write_file")), "build an app", "");

        assertThat(run.answer()).isEqualTo("Here's a plan for the app; shall I proceed?");   // no overclaim → untouched
    }
}
