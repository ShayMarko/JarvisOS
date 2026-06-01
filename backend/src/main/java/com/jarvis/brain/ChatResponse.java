package com.jarvis.brain;

import java.util.List;

import com.jarvis.agent.Step;

/** The Brain's reply to a chat request: the answer plus a transparent trace. */
public record ChatResponse(
        String answer,
        String agent,
        List<Step> steps,
        String taskId,
        int tokens,
        String model
) {}
