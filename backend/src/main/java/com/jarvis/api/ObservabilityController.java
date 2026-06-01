package com.jarvis.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.observability.AgentRunRecord;
import com.jarvis.observability.ObservabilityService;

/** Agent Debugger / Observability + Cost Monitor endpoints (spec §13). */
@RestController
@RequestMapping("/api")
public class ObservabilityController {

    private final ObservabilityService observability;
    private final Orchestrator orchestrator;

    public ObservabilityController(ObservabilityService observability, Orchestrator orchestrator) {
        this.observability = observability;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/runs")
    public List<AgentRunRecord> runs(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return observability.recent(limit);
    }

    @GetMapping("/runs/{id}")
    public AgentRunRecord run(@PathVariable String id) {
        return observability.get(id);
    }

    @GetMapping("/costs")
    public Map<String, Object> costs(@RequestParam(name = "limit", defaultValue = "200") int limit) {
        return observability.costSummary(limit);
    }

    /** Replay a recorded run by re-issuing its original request. */
    @PostMapping("/runs/{id}/replay")
    public ChatResponse replay(@PathVariable String id) {
        return orchestrator.handle(observability.get(id).getRequest());
    }
}
