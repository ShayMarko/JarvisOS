package com.jarvis.ai.tools;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.reminder.ReminderService;

import lombok.RequiredArgsConstructor;

/**
 * Schedules a ONE-OFF reminder or deferred task that fires once at an absolute time. The model resolves
 * a relative phrase ("in an hour", "next Tuesday at 3pm", "a week from now") to an absolute {@code at}
 * using the current date/time it's been given. For recurring jobs use {@code create_routine} instead.
 */
@Component
@RequiredArgsConstructor
public class CreateReminderTool implements Tool {

    private final ReminderService reminders;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_reminder",
                "Schedule a one-off reminder or deferred task to fire ONCE at a specific time. Provide 'message' "
                + "(what to remind/do) and the absolute time as 'at' (ISO-8601, e.g. 2026-06-14T20:00:00Z or "
                + "2026-06-14T20:00) — resolve any relative phrasing to an absolute time first using the current "
                + "date/time. Or give 'in_minutes' for a quick relative offset. Set 'execute' true to RUN the "
                + "message through Jarvis when due (a deferred task); omit/false to just notify you. For repeating "
                + "schedules use create_routine instead.",
                "{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"},"
                + "\"at\":{\"type\":\"string\",\"description\":\"absolute ISO-8601 time\"},"
                + "\"in_minutes\":{\"type\":\"number\"},"
                + "\"execute\":{\"type\":\"boolean\",\"description\":\"run the message as a task when due\"}},"
                + "\"required\":[\"message\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        String message = ToolArgs.firstStr(mapper, args, "message", "task", "text");
        if (message.isBlank()) {
            return "Error: provide the 'message' to remind about.";
        }
        Instant fireAt;
        try {
            fireAt = resolveTime(ToolArgs.firstStr(mapper, args, "at", "when", "time"),
                    ToolArgs.firstStr(mapper, args, "in_minutes", "minutes"));
        } catch (RuntimeException e) {
            return "Couldn't understand the time: " + e.getMessage()
                    + " — pass 'at' as ISO-8601 (e.g. 2026-06-14T20:00) or 'in_minutes'.";
        }
        if (fireAt == null) {
            return "I need a time — pass 'at' (ISO-8601) or 'in_minutes'.";
        }
        if (fireAt.isBefore(Instant.now().minusSeconds(60))) {
            return "That time (" + fireAt + ") is in the past — give a future time.";
        }
        boolean execute = Boolean.parseBoolean(ToolArgs.firstStr(mapper, args, "execute", "run", "run_task"));
        reminders.schedule(message, fireAt, execute, "chat");
        String when = ZonedDateTime.ofInstant(fireAt, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE, yyyy-MM-dd HH:mm"));
        return (execute ? "✅ Task scheduled for " : "✅ Reminder set for ") + when + ": " + message;
    }

    /** Parse an absolute ISO time, falling back to a relative minute offset. Returns null if neither given. */
    private static Instant resolveTime(String at, String inMinutes) {
        if (at != null && !at.isBlank()) {
            String s = at.trim();
            try { return Instant.parse(s); } catch (Exception ignored) { /* try next */ }
            try { return OffsetDateTime.parse(s).toInstant(); } catch (Exception ignored) { /* try next */ }
            try { return LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant(); } catch (Exception ignored) { /* try next */ }
            try { return LocalDate.parse(s).atStartOfDay(ZoneId.systemDefault()).toInstant(); } catch (Exception ignored) { /* give up */ }
            throw new IllegalArgumentException("'" + s + "' isn't a recognisable ISO time");
        }
        if (inMinutes != null && !inMinutes.isBlank()) {
            try {
                long mins = (long) Double.parseDouble(inMinutes.trim());
                return Instant.now().plusSeconds(mins * 60);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + inMinutes + "' isn't a number of minutes");
            }
        }
        return null;
    }
}
