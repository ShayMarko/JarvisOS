package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.job.Job;
import com.jarvis.job.JobService;

import lombok.RequiredArgsConstructor;

/**
 * Lets the Brain OFFLOAD a long task (build an app, deep research, write a newsletter) to the background job
 * queue and return immediately, so a voice/phone caller isn't left waiting. Jarvis notifies (Discord/bell)
 * when the job finishes. Use only for genuinely long, self-contained work — not quick answers.
 */
@Component
@RequiredArgsConstructor
public class RunInBackgroundTool implements Tool {

    private final JobService jobs;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("run_in_background",
                "Run a long, self-contained task in the background and return immediately; the user is "
                        + "notified when it finishes. Use for big builds / deep research / multi-step jobs, "
                        + "NOT for quick answers.",
                "{\"type\":\"object\",\"properties\":{\"task\":{\"type\":\"string\","
                        + "\"description\":\"the full task to run in the background\"}},\"required\":[\"task\"]}");
    }

    @Override
    public String execute(String args) {
        String task = ToolArgs.firstStr(mapper, args, "task", "request", "prompt", "input");
        if (task == null || task.isBlank()) {
            return "Provide a 'task' to run in the background.";
        }
        Job j = jobs.submit(task, "agent", "agent");
        return "Started background job " + j.getId() + ". I'll notify you when it's done.";
    }

    @Override
    public boolean mutates() {
        return true;
    }
}
