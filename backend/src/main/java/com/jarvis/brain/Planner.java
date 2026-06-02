package com.jarvis.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.agent.AgentDefinition;
import com.jarvis.agent.AgentSelector;

import lombok.RequiredArgsConstructor;

/**
 * Task decomposition (spec §6 "Planner / Task Decomposition"). Splits a request
 * into ordered sub-tasks and routes each to the best agent. A single-intent
 * request yields a one-step plan (the Brain then runs it as a normal single-agent
 * turn); a compound request ("do X then Y") yields a multi-step plan the
 * Orchestrator runs in parallel and merges.
 *
 * <p>v1 decomposes heuristically (connective splitting) so it works fully offline;
 * an LLM planner can drop in behind this same interface later for richer DAGs.
 */
@Component
@RequiredArgsConstructor
public class Planner {

    // Split on "and then" / "then" / ";" / newlines / numbered list markers.
    private static final String SPLIT = "(?i)\\s+and\\s+then\\s+|\\s+then\\s+|\\s*;\\s*|\\n+|\\s+\\d+\\.\\s+";

    private final AgentSelector selector;

    public List<PlanStep> plan(String message) {
        String msg = message == null ? "" : message.strip();
        List<String> fragments = Arrays.stream(msg.split(SPLIT))
                .map(String::strip)
                .filter(s -> s.length() > 2)
                .toList();

        if (fragments.size() <= 1) {
            AgentDefinition a = selector.select(msg);
            return List.of(new PlanStep("s1", a.slug(), a.name(), msg));
        }

        List<PlanStep> steps = new ArrayList<>();
        int i = 1;
        for (String fragment : fragments) {
            AgentDefinition a = selector.select(fragment);
            steps.add(new PlanStep("s" + (i++), a.slug(), a.name(), fragment));
        }
        return steps;
    }
}
