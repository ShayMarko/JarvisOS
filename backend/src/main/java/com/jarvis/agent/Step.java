package com.jarvis.agent;

/**
 * One step in the Brain/agent trace, surfaced to the user for transparency
 * (spec §13 observability). {@code kind} is one of intent | agent | tool | answer.
 */
public record Step(String kind, String label, String detail) {}
