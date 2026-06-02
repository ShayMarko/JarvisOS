package com.jarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.LanguageModel;

class AgentSelectorTest {

    private AgentSelector selector() {
        return new AgentSelector(new AgentRegistry(), mock(LanguageModel.class), new JarvisAiProperties());
    }

    @Test
    void keywordRoutesToSpecialist() {
        assertThat(selector().byKeyword("check my cpu and ram").slug()).isEqualTo("system");
        assertThat(selector().byKeyword("read the file in my folder").slug()).isEqualTo("files");
        assertThat(selector().byKeyword("research this on the web").slug()).isEqualTo("research");
    }

    @Test
    void unmatchedFallsBackToGeneralOnMock() {
        // default provider = mock → no LLM routing → General fallback
        assertThat(selector().select("write me a haiku about the sea").slug()).isEqualTo("general");
    }

    @Test
    void resolveUsesModelSlugThenKeyword() {
        AgentSelector s = selector();
        assertThat(s.resolve("backend", "anything").slug()).isEqualTo("backend");        // valid slug honored
        assertThat(s.resolve("not-a-real-agent", "check cpu").slug()).isEqualTo("system"); // bad slug → keyword
    }

    @Test
    void rosterListsManyAgents() {
        assertThat(selector().roster().lines().count()).isGreaterThan(20);
    }
}
