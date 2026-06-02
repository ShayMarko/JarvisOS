package com.jarvis.observability;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.jarvis.error.Exceptions.NotFoundException;

/** Agent Debugger / Observability store (spec §13.2) + Cost/Token Monitor (§13.1). */
@Service
@RequiredArgsConstructor
public class ObservabilityService {

    private final AgentRunRepository repository;


    public AgentRunRecord record(String taskId, String sessionId, String agent, String model, String request,
                                 String answer, String status, int promptTokens, int completionTokens,
                                 double cost, long durationMs, String stepsJson) {
        return repository.save(new AgentRunRecord(
                "arun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                taskId, sessionId, agent, model, request, answer, status,
                promptTokens, completionTokens, cost, durationMs, stepsJson));
    }

    public List<AgentRunRecord> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(limit, 500)));
    }

    public AgentRunRecord get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("No run " + id));
    }

    /** Cost / Token Monitor summary over recent runs. */
    public Map<String, Object> costSummary(int limit) {
        List<AgentRunRecord> runs = recent(limit);
        long promptTokens = 0;
        long completionTokens = 0;
        double cost = 0;
        Map<String, Double> byModel = new LinkedHashMap<>();
        Map<String, Integer> byAgent = new LinkedHashMap<>();
        for (AgentRunRecord r : runs) {
            promptTokens += r.getPromptTokens();
            completionTokens += r.getCompletionTokens();
            cost += r.getCost();
            byModel.merge(r.getModel() == null ? "?" : r.getModel(), r.getCost(), Double::sum);
            byAgent.merge(r.getAgent() == null ? "?" : r.getAgent(), 1, Integer::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", runs.size());
        out.put("promptTokens", promptTokens);
        out.put("completionTokens", completionTokens);
        out.put("totalTokens", promptTokens + completionTokens);
        out.put("totalCost", Math.round(cost * 1e6) / 1e6);
        out.put("costByModel", byModel);
        out.put("runsByAgent", byAgent);
        return out;
    }

    /** The three real AI providers shown in the Token dashboard (mock/offline is excluded). */
    private static final List<String> PROVIDERS = List.of("ollama", "openai", "anthropic");

    /** Map a model id (e.g. "claude:opus", "openai:gpt-4o-mini", "ollama:llama3", "mock-local") to its provider. */
    static String providerOf(String model) {
        if (model == null) {
            return "mock";
        }
        String m = model.toLowerCase();
        if (m.startsWith("claude") || m.startsWith("anthropic")) {
            return "anthropic";
        }
        if (m.startsWith("openai") || m.startsWith("gpt")) {
            return "openai";
        }
        if (m.startsWith("ollama")) {
            return "ollama";
        }
        return "mock";
    }

    /**
     * Per-PROVIDER token/cost breakdown (Ollama / OpenAI / Anthropic) + a chronological
     * usage timeline, for the Token dashboard. Mock (offline stub) is excluded — it isn't
     * a real AI provider and its "cost" isn't real money.
     */
    public Map<String, Object> tokenDashboard(int limit) {
        List<AgentRunRecord> runs = recent(limit);
        Map<String, long[]> perProvider = new LinkedHashMap<>();   // provider -> [prompt, completion, runs]
        Map<String, Double> perProviderCost = new LinkedHashMap<>();
        PROVIDERS.forEach(p -> { perProvider.put(p, new long[3]); perProviderCost.put(p, 0.0); });
        long prompt = 0;
        long completion = 0;
        double cost = 0;
        int counted = 0;
        for (AgentRunRecord r : runs) {
            String provider = providerOf(r.getModel());
            if (!PROVIDERS.contains(provider)) {
                continue;   // skip mock/offline
            }
            long[] t = perProvider.get(provider);
            t[0] += r.getPromptTokens();
            t[1] += r.getCompletionTokens();
            t[2] += 1;
            perProviderCost.merge(provider, r.getCost(), Double::sum);
            prompt += r.getPromptTokens();
            completion += r.getCompletionTokens();
            cost += r.getCost();
            counted++;
        }

        // Always emit all three providers (0s if unused) so the UI shows the full set of tabs.
        List<Map<String, Object>> providers = new ArrayList<>();
        for (String provider : PROVIDERS) {
            long[] t = perProvider.get(provider);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("provider", provider);
            m.put("runs", t[2]);
            m.put("promptTokens", t[0]);
            m.put("completionTokens", t[1]);
            m.put("totalTokens", t[0] + t[1]);
            m.put("cost", Math.round(perProviderCost.get(provider) * 1e6) / 1e6);
            providers.add(m);
        }

        // Chronological timeline (oldest→newest) of real-provider runs, tagged by provider.
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (int i = runs.size() - 1; i >= 0; i--) {
            AgentRunRecord r = runs.get(i);
            String provider = providerOf(r.getModel());
            if (!PROVIDERS.contains(provider)) {
                continue;
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("at", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
            p.put("provider", provider);
            p.put("model", r.getModel());
            p.put("tokens", r.getPromptTokens() + r.getCompletionTokens());
            p.put("cost", Math.round(r.getCost() * 1e6) / 1e6);
            timeline.add(p);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", counted);
        out.put("promptTokens", prompt);
        out.put("completionTokens", completion);
        out.put("totalTokens", prompt + completion);
        out.put("totalCost", Math.round(cost * 1e6) / 1e6);
        out.put("providers", providers);
        out.put("timeline", timeline);
        return out;
    }
}
