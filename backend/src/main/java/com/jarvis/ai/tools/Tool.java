package com.jarvis.ai.tools;

import com.jarvis.ai.ToolSpec;

/**
 * A capability exposed to agents as a callable tool (the diagram's "Tool /
 * Capability Selector"). Tools are the bridge between the reasoning layer and
 * the real Capabilities (files, system, memory, …).
 */
public interface Tool {

    ToolSpec spec();

    /** Run the tool with JSON arguments; return a human/model-readable result. */
    String execute(String argumentsJson);

    /**
     * True if this tool has a real side effect — it creates/changes an artifact or acts on the outside
     * world (writes a file, sends a message, saves to memory, …). Read-only tools (search, read, status)
     * return false. The agent runtime uses this to tell whether the agent actually ACCOMPLISHED something
     * this turn, so it can catch (and correct) an answer that claims an action that never succeeded.
     */
    default boolean mutates() {
        return false;
    }
}
