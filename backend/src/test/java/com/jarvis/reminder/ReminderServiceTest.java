package com.jarvis.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jarvis.brain.Orchestrator;
import com.jarvis.notification.NotificationService;

class ReminderServiceTest {

    @Test
    void firesDueReminderAsNotification() {
        ReminderRepository repo = mock(ReminderRepository.class);
        NotificationService notif = mock(NotificationService.class);
        Orchestrator orch = mock(Orchestrator.class);
        ReminderService svc = new ReminderService(repo, notif, orch);

        Reminder r = new Reminder("rem_1", "Call mom", Instant.now().minusSeconds(5), false, "chat");
        when(repo.findByFiredFalseAndFireAtLessThanEqualOrderByFireAtAsc(any()))
                .thenReturn(new ArrayList<>(List.of(r)));

        svc.sweep();

        assertThat(r.isFired()).isTrue();
        verify(notif).notify(eq("info"), eq("⏰ Reminder"), eq("Call mom"), eq("reminder"));
        verifyNoInteractions(orch);   // notify-only reminder never touches the Brain
    }

    @Test
    void doesNothingWhenNothingDue() {
        ReminderRepository repo = mock(ReminderRepository.class);
        NotificationService notif = mock(NotificationService.class);
        ReminderService svc = new ReminderService(repo, notif, mock(Orchestrator.class));
        when(repo.findByFiredFalseAndFireAtLessThanEqualOrderByFireAtAsc(any())).thenReturn(new ArrayList<>());

        svc.sweep();

        verifyNoInteractions(notif);
    }
}
