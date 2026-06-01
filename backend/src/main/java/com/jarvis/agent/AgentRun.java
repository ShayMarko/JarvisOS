package com.jarvis.agent;

import java.util.List;

/** The result of running an agent: final answer, trace, token usage, model. */
public record AgentRun(String answer, List<Step> steps, int promptTokens, int completionTokens, String model) {

    public int tokens() {
        return promptTokens + completionTokens;
    }
}
