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
    void tradingIntentsRouteToTheTradingAgent() {
        assertThat(selector().byKeyword("give me a swing trade read on BTCUSDT").slug()).isEqualTo("trading");
        assertThat(selector().byKeyword("what's the wyckoff structure on the market right now").slug()).isEqualTo("trading");
    }

    @Test
    void notionIntentsRouteToTheNotionAgent() {
        assertThat(selector().byKeyword("design a notion template for habit tracking").slug()).isEqualTo("notion");
    }

    @Test
    void bookIntentsRouteToTheAuthorAgent() {
        assertThat(selector().byKeyword("write me a book about stoicism").slug()).isEqualTo("author");
        assertThat(selector().byKeyword("format my ebook for kdp").slug()).isEqualTo("author");
    }

    @Test
    void devWorkflowIntentsRouteToDevflow() {
        assertThat(selector().byKeyword("review the pull request #12 in acme/app").slug()).isEqualTo("devflow");
        assertThat(selector().byKeyword("triage the open github issues").slug()).isEqualTo("devflow");
    }

    @Test
    void buildIntentOutranksDomainKeywords() {
        // The bug from the transcript: "email"/"calendar" mentioned in a BUILD request hijacked
        // routing to the Email/Calendar agent (which can't write files). Build intent must win.
        assertThat(selector().byKeyword("create fullstack app for reminders with email calendar etc").slug()).isEqualTo("code");
        assertThat(selector().byKeyword("build a reminder app").slug()).isEqualTo("code");
        assertThat(selector().byKeyword("make me a dashboard with email integration").slug()).isEqualTo("code");
        // ...but a genuine email/calendar request (no build intent) still routes correctly.
        assertThat(selector().byKeyword("reply to that email in my inbox").slug()).isEqualTo("email");
        assertThat(selector().byKeyword("what's on my calendar today").slug()).isEqualTo("calendar");
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
    void authorAndBookCriticAreRegistered() {
        AgentRegistry r = new AgentRegistry();
        assertThat(r.find("author")).isPresent();
        assertThat(r.find("bookcritic")).isPresent();
    }

    @Test
    void rosterListsManyAgents() {
        assertThat(selector().roster().lines().count()).isGreaterThan(20);
    }
}
