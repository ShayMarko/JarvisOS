package com.jarvis.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.reminder.ReminderService;

class CreateReminderToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void schedulesAtAbsoluteIsoTime() {
        ReminderService svc = mock(ReminderService.class);
        CreateReminderTool tool = new CreateReminderTool(svc, mapper);
        String future = Instant.now().plusSeconds(7200).toString();   // ISO-8601 with Z

        String out = tool.execute("{\"message\":\"ping me\",\"at\":\"" + future + "\"}");

        assertThat(out).startsWith("✅ Reminder set");
        verify(svc).schedule(eq("ping me"), any(Instant.class), eq(false), eq("chat"));
    }

    @Test
    void schedulesRelativeMinutesAsExecutableTask() {
        ReminderService svc = mock(ReminderService.class);
        CreateReminderTool tool = new CreateReminderTool(svc, mapper);

        String out = tool.execute("{\"message\":\"summarise inbox\",\"in_minutes\":90,\"execute\":true}");

        assertThat(out).startsWith("✅ Task scheduled");
        verify(svc).schedule(eq("summarise inbox"), any(Instant.class), eq(true), eq("chat"));
    }

    @Test
    void rejectsPastTime() {
        ReminderService svc = mock(ReminderService.class);
        CreateReminderTool tool = new CreateReminderTool(svc, mapper);
        String past = Instant.now().minusSeconds(3600).toString();

        String out = tool.execute("{\"message\":\"x\",\"at\":\"" + past + "\"}");

        assertThat(out).contains("past");
        verify(svc, never()).schedule(any(), any(), anyBoolean(), any());
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
