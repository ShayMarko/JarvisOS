package com.jarvis.ai.tools;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.workflow.StepType;
import com.jarvis.workflow.TriggerType;
import com.jarvis.workflow.WorkflowDraft;
import com.jarvis.workflow.WorkflowService;
import com.jarvis.workflow.WorkflowStep;

import lombok.RequiredArgsConstructor;

/**
 * Turns a natural-language routine ("every morning, summarise my email and post it to Discord") into a
 * real, durable scheduled workflow: one BRAIN step running the task on a cron, created + scheduled via
 * the existing workflow engine. The model supplies the task and either an explicit 6-field 'cron' or a
 * plain-English 'schedule' that this tool maps to cron for common cases.
 */
@Component
@RequiredArgsConstructor
public class CreateRoutineTool implements Tool {

    /** @Lazy breaks the bean cycle ToolRegistry→CreateRoutineTool→WorkflowService→…→Orchestrator→ToolRegistry. */
    @Lazy
    private final WorkflowService workflows;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_routine",
                "Create a recurring routine: run a task automatically on a schedule. Provide 'task' (what Jarvis "
                + "should do each time) and EITHER 'cron' (6-field Spring cron) OR 'schedule' in plain English "
                + "(e.g. 'every morning', 'daily at 18:30', 'hourly', 'every 15 minutes'). Optional 'name'.",
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"task\":{\"type\":\"string\"},"
                + "\"cron\":{\"type\":\"string\"},\"schedule\":{\"type\":\"string\"}},\"required\":[\"task\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        String task = ToolArgs.firstStr(mapper, args, "task", "do", "prompt");
        if (task.isBlank()) {
            return "Error: provide the 'task' the routine should run.";
        }
        String cron = ToolArgs.firstStr(mapper, args, "cron");
        if (!isCron(cron)) {
            cron = toCron(ToolArgs.firstStr(mapper, args, "schedule", "when", "every"));
        }
        if (cron == null) {
            return "I need a time — give a 'cron' (6-field) or a 'schedule' like 'every morning', "
                    + "'daily at 18:30', 'hourly', or 'every 15 minutes'.";
        }
        String name = ToolArgs.firstStr(mapper, args, "name");
        if (name.isBlank()) {
            name = "Routine: " + (task.length() > 40 ? task.substring(0, 40) + "…" : task);
        }
        WorkflowStep step = new WorkflowStep(
                "st_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                "Run task", StepType.BRAIN, Map.of("prompt", task), 1);
        WorkflowDraft draft = new WorkflowDraft(name, "Auto-created routine.", TriggerType.SCHEDULE, cron, true, List.of(step));
        try {
            workflows.create(draft);
            return "✅ Routine created: '" + name + "' on cron '" + cron + "'. Each run it will: " + task;
        } catch (Exception e) {
            return "Couldn't create the routine: " + e.getMessage();
        }
    }

    /** Accept an explicit 6-field Spring cron as-is. */
    private static boolean isCron(String s) {
        return s != null && s.trim().split("\\s+").length == 6;
    }

    private static final Pattern AT_TIME = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?");
    private static final Pattern EVERY_MIN = Pattern.compile("every\\s+(\\d{1,2})\\s*min");
    private static final Pattern EVERY_HRS = Pattern.compile("every\\s+(\\d{1,2})\\s*hour");

    /** Map a plain-English schedule to a 6-field Spring cron, or null if not understood. */
    static String toCron(String phraseRaw) {
        if (phraseRaw == null || phraseRaw.isBlank()) {
            return null;
        }
        String p = phraseRaw.toLowerCase().trim();
        if (p.contains("morning")) return "0 0 8 * * *";
        if (p.contains("noon") || p.contains("midday")) return "0 0 12 * * *";
        if (p.contains("evening")) return "0 0 18 * * *";
        if (p.contains("night")) return "0 0 21 * * *";
        if (p.contains("midnight")) return "0 0 0 * * *";
        if (p.equals("hourly") || p.contains("every hour")) return "0 0 * * * *";

        Matcher mins = EVERY_MIN.matcher(p);
        if (mins.find()) return "0 */" + Integer.parseInt(mins.group(1)) + " * * * *";
        Matcher hrs = EVERY_HRS.matcher(p);
        if (hrs.find()) return "0 0 */" + Integer.parseInt(hrs.group(1)) + " * * *";

        // "daily at 18:30", "every day at 6pm", "at 9"
        if (p.contains("at ") || p.contains("daily") || p.contains("every day")) {
            Matcher t = AT_TIME.matcher(p.contains("at ") ? p.substring(p.indexOf("at ") + 3) : p);
            if (t.find()) {
                int hour = Integer.parseInt(t.group(1));
                int min = t.group(2) == null ? 0 : Integer.parseInt(t.group(2));
                String ampm = t.group(3);
                if ("pm".equals(ampm) && hour < 12) hour += 12;
                if ("am".equals(ampm) && hour == 12) hour = 0;
                if (hour >= 0 && hour <= 23 && min >= 0 && min <= 59) {
                    return "0 " + min + " " + hour + " * * *";
                }
            }
            if (p.contains("daily") || p.contains("every day")) return "0 0 9 * * *";   // sensible default
        }
        return null;
    }
}
