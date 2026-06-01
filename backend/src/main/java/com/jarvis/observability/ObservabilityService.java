package com.jarvis.observability;

import lombok.RequiredArgsConstructor;

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
}
