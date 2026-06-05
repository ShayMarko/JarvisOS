package com.jarvis.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jarvis.discord.DiscordService;
import com.jarvis.telegram.TelegramService;

class NotificationServiceTest {

    private NotificationService service(NotificationRepository repo) {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        return new NotificationService(repo, mock(TelegramService.class), mock(DiscordService.class));
    }

    @Test
    void approvalNotificationCarriesActionId() {
        NotificationRepository repo = mock(NotificationRepository.class);
        NotificationService svc = service(repo);

        Notification n = svc.notify("warning", "Approval needed", "Run rm (risk HIGH)", "approval", "ap_123");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getActionId()).isEqualTo("ap_123");
        assertThat(cap.getValue().getSource()).isEqualTo("approval");
        assertThat(n.getActionId()).isEqualTo("ap_123");
    }

    @Test
    void plainNotificationHasNoActionId() {
        Notification n = service(mock(NotificationRepository.class)).notify("info", "Hi", "body", "system");
        assertThat(n.getActionId()).isNull();
    }
}
