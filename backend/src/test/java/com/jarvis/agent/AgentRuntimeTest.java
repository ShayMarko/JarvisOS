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

    private static AgentDefinition generalAgent(java.util.List<String> tools) {
        return new AgentDefinition("General", "general", "general", "You are Jarvis.", tools, "general");
    }

    @Test
    void reflectsOnRefusalAndUsesAToolInstead() {
        // Model first refuses ("I can't get the weather"); the runtime hands it its tools and it retries.
        JarvisAiProperties props = new JarvisAiProperties();
        props.setProvider("ollama");   // a real model is active → reflection is meaningful
        ToolRegistry registry = new ToolRegistry(List.of(new FakeSearch()));
        LanguageModel model = new ScriptedModel(
                ModelResponse.text("I can't check the weather — I don't have real-time access.", 5, 5),
                ModelResponse.tools(List.of(call("search_files")), 5, 5),
                ModelResponse.text("It's 25°C and sunny in Tel Aviv.", 5, 5));
        AgentRuntime runtime = new AgentRuntime(model, registry, props, new JarvisPersonaProperties());

        AgentRun run = runtime.run(generalAgent(List.of("search_files")), "weather in tel aviv", "");

        assertThat(run.answer()).isEqualTo("It's 25°C and sunny in Tel Aviv.");      // it figured it out
        assertThat(run.steps()).anyMatch(s -> s.kind().equals("intent") && s.label().contains("Re-checking"));
    }

    @Test
    void keepsWritingFilesWhenItDumpsCodeInChat() {
        // Wrote one file, then narrated the rest as a code block → runtime pushes it to keep writing.
        JarvisAiProperties props = new JarvisAiProperties();
        props.setProvider("ollama");
        ToolRegistry registry = new ToolRegistry(List.of(new OkWrite()));
        LanguageModel model = new ScriptedModel(
                ModelResponse.tools(List.of(call("write_file")), 5, 5),                       // file 1
                ModelResponse.text("Here's the rest:\n```java\npublic class App {}\n```", 5, 5), // dumps code → nudge
                ModelResponse.tools(List.of(call("write_file")), 5, 5),                       // file 2 (after nudge)
                ModelResponse.text("Built the app under Projects/foo. Run: mvn spring-boot:run.", 5, 5))
        ;
        AgentRuntime runtime = new AgentRuntime(model, registry, props, new JarvisPersonaProperties())
        ;
        AgentRun run = runtime.run(codeAgent(List.of("write_file")), "build an app", "")
        ;
        long writes = run.steps().stream().filter(s -> s.kind().equals("tool")).count()
        ;
        assertThat(writes).isEqualTo(2);   // it wrote a SECOND file instead of stopping
        assertThat(run.steps()).anyMatch(s -> s.label().contains("Writing the remaining files"));
        assertThat(run.answer()).isEqualTo("Built the app under Projects/foo. Run: mvn spring-boot:run.");
    }

    @Test
    void pushesToWriteWhenItNarratesAWholeProjectWithoutWritingAnything() {
        // qwen sometimes dumps the whole project as several ```python blocks and calls write_file zero
        // times. A build-capable agent should be pushed to actually write the files.
        JarvisAiProperties props = new JarvisAiProperties();
        props.setProvider("ollama");
        ToolRegistry registry = new ToolRegistry(List.of(new OkWrite()));
        String multiBlock = "Here's the app:\n```python\nclass A: pass\n```\nand:\n```python\nclass B: pass\n```";
        LanguageModel model = new ScriptedModel(
                ModelResponse.text(multiBlock, 5, 5),                            // narrates, writes nothing
                ModelResponse.tools(List.of(call("write_file")), 5, 5),          // after nudge, writes a file
                ModelResponse.text("Built it under Projects/app. Run: python run.py", 5, 5));
        AgentRuntime runtime = new AgentRuntime(model, registry, props, new JarvisPersonaProperties());

        AgentRun run = runtime.run(codeAgent(List.of("write_file")), "build a desktop todo app", "");

        assertThat(run.steps()).anyMatch(s -> s.kind().equals("tool"));   // it actually wrote a file
        assertThat(run.steps()).anyMatch(s -> s.label().contains("Writing the remaining files"));
        assertThat(run.answer()).isEqualTo("Built it under Projects/app. Run: python run.py");
    }

    @Test
    void stripsCodeFromChatIfTheModelWontStopNarrating() {
        // Headless guarantee: even if the model dumps code on every turn, the final chat answer must
        // contain NO code (it lives in the written files).
        JarvisAiProperties props = new JarvisAiProperties();
        props.setProvider("ollama");
        ToolRegistry registry = new ToolRegistry(List.of(new OkWrite()));
        java.util.List<ModelResponse> resps = new java.util.ArrayList<>();
        resps.add(ModelResponse.tools(List.of(call("write_file")), 5, 5));       // wrote a file
        for (int i = 0; i < 8; i++) {                                            // then keeps dumping code
            resps.add(ModelResponse.text("Done! Here's the code:\n```python\nx = 1\n```", 5, 5));
        }
        LanguageModel model = new ScriptedModel(resps.toArray(new ModelResponse[0]));
        AgentRuntime runtime = new AgentRuntime(model, registry, props, new JarvisPersonaProperties());

        AgentRun run = runtime.run(codeAgent(List.of("write_file")), "build an app", "");

        assertThat(run.answer()).doesNotContain("```");          // no code ever reaches chat
        assertThat(run.answer()).containsIgnoringCase("headless");
    }

    @Test
    void neverShowsALeakedToolCallJsonInChat() {
        // If a (garbled) tool-call JSON survives as the answer, a build agent shows an honest retry
        // message — never the raw JSON.
        JarvisAiProperties props = new JarvisAiProperties();
        props.setProvider("ollama");
        ToolRegistry registry = new ToolRegistry(List.of(new OkWrite()));
        LanguageModel model = new ScriptedModel(ModelResponse.text(
                "```json\n{\"name\": \"write_file\", \"arguments\": {\"path\": \"a.py\", \"content\": \"x\"}}\n``` run it", 5, 5));
        AgentRuntime runtime = new AgentRuntime(model, registry, props, new JarvisPersonaProperties());

        AgentRun run = runtime.run(codeAgent(List.of("write_file")), "build x", "");

        assertThat(run.answer()).doesNotContain("```");
        assertThat(run.answer()).doesNotContain("write_file");
        assertThat(run.answer()).containsIgnoringCase("try again");
    }

    @Test
    void doesNotHijackAPlainCodeSnippetAnswer() {
        // No file was written → "show me a snippet" answers keep their code block, untouched.
        JarvisAiProperties props = new JarvisAiProperties();
        props.setProvider("ollama");
        ToolRegistry registry = new ToolRegistry(List.of(new OkWrite()));
        LanguageModel model = new ScriptedModel(
                ModelResponse.text("Sure:\n```js\nconsole.log(1)\n```", 5, 5));
        AgentRuntime runtime = new AgentRuntime(model, registry, props, new JarvisPersonaProperties());

        AgentRun run = runtime.run(codeAgent(List.of("write_file")), "show me a js one-liner", "");

        assertThat(run.answer()).contains("console.log(1)");
        assertThat(run.steps()).noneMatch(s -> s.label().contains("Writing the remaining files"));
    }

    @Test
    void doesNotReflectWhenTheAgentHasNoTools() {
        JarvisAiProperties props = new JarvisAiProperties();
        props.setProvider("ollama");
        ToolRegistry registry = new ToolRegistry(List.of());
        LanguageModel model = new ScriptedModel(ModelResponse.text("I can't do that.", 5, 5));
        AgentRuntime runtime = new AgentRuntime(model, registry, props, new JarvisPersonaProperties());

        AgentRun run = runtime.run(generalAgent(List.of()), "do something", "");

        assertThat(run.answer()).isEqualTo("I can't do that.");   // nothing to reflect with → unchanged
    }

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
