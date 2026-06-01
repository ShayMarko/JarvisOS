package com.jarvis.workflow;

import java.util.Map;

/**
 * One step of a workflow (spec §12). {@code config} holds type-specific fields
 * (e.g. {command}, {prompt}, {connector,action,args}, {message}, {title,why}).
 */
public record WorkflowStep(String id, String name, StepType type, Map<String, Object> config, int maxAttempts) {

    public int attemptsOrDefault() {
        return maxAttempts <= 0 ? 1 : maxAttempts;
    }

    public String configString(String key) {
        Object v = config == null ? null : config.get(key);
        return v == null ? "" : v.toString();
    }
}
