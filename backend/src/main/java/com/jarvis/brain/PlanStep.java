package com.jarvis.brain;

/**
 * One node of a {@link Planner} plan: a sub-task routed to a specific agent.
 *
 * @param id        stable step id (s1, s2, …)
 * @param agentSlug the agent chosen to handle this sub-task
 * @param agentName the agent's display name
 * @param task      the sub-task text handed to that agent
 */
public record PlanStep(String id, String agentSlug, String agentName, String task) {}
