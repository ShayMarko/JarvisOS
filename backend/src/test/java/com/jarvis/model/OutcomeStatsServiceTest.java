package com.jarvis.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.jarvis.observability.AgentRunRecord;

/** The pure learned-routing decision: cheapest model that has actually proven itself for an agent. */
class OutcomeStatsServiceTest {

    private AgentRunRecord run(String agent, String model, String status, double cost) {
        return new AgentRunRecord("arun", "t", "s", agent, model, "req", "ans", status, 10, 20, cost, 100, "[]");
    }

    @Test
    void prefersTheCheapestProvenModel() {
        List<AgentRunRecord> runs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            runs.add(run("Code Agent", "ollama", "OK", 0.0));     // free + reliable
            runs.add(run("Code Agent", "opus", "OK", 0.20));      // reliable but pricey
        }
        var rec = OutcomeStatsService.recommendModelId(runs, Set.of("ollama", "opus"), 4, 0.7);
        assertThat(rec).contains("ollama");                       // free wins on score
    }

    @Test
    void ignoresModelsBelowMinSamples() {
        List<AgentRunRecord> runs = new ArrayList<>();
        runs.add(run("Code Agent", "opus", "OK", 0.20));          // only 1 sample
        var rec = OutcomeStatsService.recommendModelId(runs, Set.of("opus"), 4, 0.7);
        assertThat(rec).isEmpty();
    }

    @Test
    void ignoresFlakyModels() {
        List<AgentRunRecord> runs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            runs.add(run("Code Agent", "flaky", "FAILED", 0.0));  // 0% success
        }
        var rec = OutcomeStatsService.recommendModelId(runs, Set.of("flaky"), 4, 0.7);
        assertThat(rec).isEmpty();
    }

    @Test
    void ignoresUnavailableModels() {
        List<AgentRunRecord> runs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            runs.add(run("Code Agent", "retired-model", "OK", 0.0));
        }
        var rec = OutcomeStatsService.recommendModelId(runs, Set.of("ollama"), 4, 0.7);   // retired not available
        assertThat(rec).isEmpty();
    }
}
