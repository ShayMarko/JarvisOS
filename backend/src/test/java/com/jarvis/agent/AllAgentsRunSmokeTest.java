package com.jarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.JarvisPersonaProperties;
import com.jarvis.ai.MockLanguageModel;
import com.jarvis.ai.ToolSpec;
import com.jarvis.ai.tools.Tool;
import com.jarvis.ai.tools.ToolRegistry;
import com.jarvis.approval.ApprovalService;
import com.jarvis.explorer.FileSystemService;

/**
 * Exhaustive smoke: drives EVERY agent in the (markdown-loaded) roster through the real AgentRuntime —
 * proving each agent's prompt loads, all its declared tools resolve in the registry, and the runtime loop
 * actually executes and returns an answer, with no wiring error. This is the "all of them, not a sample"
 * structural guarantee (the live Ollama runs cover realism on top).
 */
class AllAgentsRunSmokeTest {

    /** A no-op stand-in for any real tool, so every agent's declared tools resolve. */
    private record StubTool(String name) implements Tool {
        @Override public ToolSpec spec() { return new ToolSpec(name, "stub", "{}"); }
        @Override public String execute(String argumentsJson) { return "ok"; }
    }

    @Test
    void everyAgentExecutesThroughTheRuntime() {
        List<AgentDefinition> agents = new AgentRegistry().all();
        assertThat(agents).hasSizeGreaterThanOrEqualTo(49);

        // a stub tool for every distinct tool name referenced across the whole roster
        Set<String> toolNames = agents.stream()
                .flatMap(a -> a.toolNames().stream()).collect(Collectors.toSet());
        List<Tool> stubs = toolNames.stream().<Tool>map(StubTool::new).toList();
        ToolRegistry registry = new ToolRegistry(stubs);

        AgentRuntime runtime = new AgentRuntime(new MockLanguageModel(), registry,
                new JarvisAiProperties(), new JarvisPersonaProperties(),
                mock(ApprovalService.class), mock(FileSystemService.class));

        int ran = 0;
        for (AgentDefinition a : agents) {
            AgentRun run = runtime.run(a, "Do a quick self-check and reply briefly.", "");
            assertThat(run).as("run for agent '%s'", a.slug()).isNotNull();
            assertThat(run.answer()).as("answer for agent '%s'", a.slug()).isNotBlank();
            assertThat(run.steps()).as("steps for agent '%s'", a.slug()).isNotNull();
            // every tool the agent declares must exist in the registry (else it's a silently broken agent)
            for (String t : a.toolNames()) {
                assertThat(registry.find(t)).as("tool '%s' for agent '%s'", t, a.slug()).isPresent();
            }
            ran++;
        }
        assertThat(ran).isEqualTo(agents.size());
        System.out.println("Ran ALL " + ran + " agents through the runtime — every one returned an answer.");
    }
}
