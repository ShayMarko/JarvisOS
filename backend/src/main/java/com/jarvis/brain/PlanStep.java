package com.jarvis.brain;

import java.util.List;

/**
 * One node of a {@link Planner} plan: a sub-task routed to a specific agent, plus
 * the ids of the steps it depends on (its inputs). An empty {@code dependsOn} means
 * the step can start immediately; the Orchestrator runs independent steps in parallel
 * and feeds each dependent step the outputs of the steps it waited on.
 *
 * @param id        stable step id (s1, s2, …)
 * @param agentSlug the agent chosen to handle this sub-task
 * @param agentName the agent's display name
 * @param task      the sub-task text handed to that agent
 * @param dependsOn ids of steps whose results this step needs first
 */
public record PlanStep(String id, String agentSlug, String agentName, String task, List<String> dependsOn) {

    /** Convenience for an independent step (no dependencies). */
    public PlanStep(String id, String agentSlug, String agentName, String task) {
        this(id, agentSlug, agentName, task, List.of());
    }
}
