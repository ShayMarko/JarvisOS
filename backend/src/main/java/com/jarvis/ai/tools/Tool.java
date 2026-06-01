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
}
