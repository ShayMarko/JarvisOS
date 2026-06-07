package com.jarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class AgentDefinitionLoaderTest {

    private static final String SAMPLE = """
            ---
            slug: demo
            name: "Demo Agent"
            role: "Does demo things: nicely."
            category: revenue
            tools: [web_search, write_file, create_chart]
            ---
            You are the Demo Agent. Do the thing, then summarise.
            """;

    @Test
    void parsesFrontmatterAndBody() {
        AgentDefinition a = AgentDefinitionLoader.parse(SAMPLE, "demo.md");
        assertThat(a.slug()).isEqualTo("demo");
        assertThat(a.name()).isEqualTo("Demo Agent");
        assertThat(a.role()).isEqualTo("Does demo things: nicely.");   // colon inside quoted value survives
        assertThat(a.category()).isEqualTo("revenue");
        assertThat(a.toolNames()).containsExactly("web_search", "write_file", "create_chart");
        assertThat(a.systemPrompt()).isEqualTo("You are the Demo Agent. Do the thing, then summarise.");
    }

    @Test
    void failsFastOnMissingSlug() {
        String bad = "---\nname: \"X\"\n---\nbody";
        assertThatThrownBy(() -> AgentDefinitionLoader.parse(bad, "bad.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void failsFastOnEmptyBody() {
        String bad = "---\nslug: x\nname: \"X\"\n---\n";
        assertThatThrownBy(() -> AgentDefinitionLoader.parse(bad, "x.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty prompt");
    }

    @Test
    void loadsTheBundledRosterFromClasspath() {
        List<AgentDefinition> agents = new AgentDefinitionLoader().load();
        assertThat(agents).hasSizeGreaterThanOrEqualTo(49);
        assertThat(agents).anyMatch(a -> a.slug().equals("general"));
        assertThat(agents.get(0).slug()).isEqualTo("general");   // general sorted first
        AgentDefinition general = agents.stream().filter(a -> a.slug().equals("general")).findFirst().orElseThrow();
        assertThat(general.toolNames()).isNotEmpty();
        assertThat(general.systemPrompt()).contains("Jarvis");
        // every agent has a non-blank prompt + at least the basics
        assertThat(agents).allSatisfy(a -> {
            assertThat(a.slug()).isNotBlank();
            assertThat(a.systemPrompt()).isNotBlank();
        });
    }
}
