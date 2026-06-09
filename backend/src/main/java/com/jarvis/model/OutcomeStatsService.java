package com.jarvis.model;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jarvis.observability.AgentRunRecord;
import com.jarvis.observability.AgentRunRepository;

import lombok.RequiredArgsConstructor;

/**
 * Self-learning routing signal: looks at the REAL run history ({@code agent_run}) and, for a given agent,
 * recommends the model that has actually performed best — the cheapest model whose past success rate clears
 * a bar over a minimum sample size. Score = success-rate ÷ avg-cost, so a free local model that keeps
 * succeeding wins on cost, while a flaky one is dropped. Reuses the existing observability table — no new
 * schema. The {@link LearnedRouter} applies this only as a quality-preserving overlay on the base router.
 */
@Service
@RequiredArgsConstructor
public class OutcomeStatsService {

    private static final int WINDOW_DAYS = 30;
    private static final int MIN_SAMPLES = 4;       // need a few runs before trusting a model for an agent
    private static final double MIN_SUCCESS = 0.7;  // and a decent success rate

    private final AgentRunRepository runs;
    private final ModelCatalog catalog;

    /** Per-(agent,model) tally over the window. */
    record Stat(String model, int runs, int successes, double sumCost) {
        double successRate() {
            return runs == 0 ? 0 : (double) successes / runs;
        }

        double avgCost() {
            return runs == 0 ? 0 : sumCost / runs;
        }

        /** Higher = better: reward success, penalise cost. Free models get a large but finite score. */
        double score() {
            return successRate() / (avgCost() + 1e-6);
        }
    }

    /** The model this agent should use based on history, if there's enough evidence; else empty. */
    public Optional<ModelDescriptor> recommend(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            return Optional.empty();
        }
        Instant since = Instant.now().minus(Duration.ofDays(WINDOW_DAYS));
        List<AgentRunRecord> recent = runs.findByCreatedAtAfter(since).stream()
                .filter(r -> agentName.equals(r.getAgent()))
                .toList();
        Set<String> available = catalog.available().stream().map(ModelDescriptor::id).collect(Collectors.toSet());
        return recommendModelId(recent, available, MIN_SAMPLES, MIN_SUCCESS).map(catalog::byId);
    }

    /**
     * Pure decision (testable without Spring): among models that are currently AVAILABLE and have at least
     * {@code minSamples} runs at ≥ {@code minSuccess} success rate for this agent, return the highest-scoring
     * model id (success-rate ÷ avg-cost). Empty if no model clears the bar.
     */
    static Optional<String> recommendModelId(List<AgentRunRecord> agentRuns, Set<String> available,
                                             int minSamples, double minSuccess) {
        Map<String, int[]> counts = new HashMap<>();   // model -> [runs, successes]
        Map<String, Double> costs = new HashMap<>();    // model -> sum cost
        for (AgentRunRecord r : agentRuns) {
            String model = r.getModel();
            if (model == null || !available.contains(model)) {
                continue;
            }
            int[] c = counts.computeIfAbsent(model, k -> new int[2]);
            c[0]++;
            if ("OK".equalsIgnoreCase(r.getStatus())) {
                c[1]++;
            }
            costs.merge(model, r.getCost(), Double::sum);
        }
        Stat best = null;
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            int n = e.getValue()[0];
            int ok = e.getValue()[1];
            Stat s = new Stat(e.getKey(), n, ok, costs.getOrDefault(e.getKey(), 0.0));
            if (n >= minSamples && s.successRate() >= minSuccess && (best == null || s.score() > best.score())) {
                best = s;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.model());
    }
}
