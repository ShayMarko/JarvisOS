package com.jarvis.notification;

import lombok.RequiredArgsConstructor;

import java.util.List;
import com.jarvis.common.Ids;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jarvis.discord.DiscordService;
import com.jarvis.telegram.TelegramService;

/** The Notification Center (spec §8). Other subsystems call {@link #notify} to surface events. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final TelegramService telegram;
    private final DiscordService discord;


    public Notification notify(String type, String title, String body, String source) {
        return notify(type, title, body, source, null);
    }

    /**
     * Surface an event, optionally linked to an actionable target ({@code actionId}) — e.g. an
     * ApprovalRequest id when {@code source == "approval"}, so the client bell can render inline
     * Approve/Decline buttons. Mirrors to Discord/Telegram like any other notification.
     */
    public Notification notify(String type, String title, String body, String source, String actionId) {
        return notify(type, title, body, source, actionId, null);
    }

    /**
     * As above, additionally tagging the notification with a risk level (LOW/MEDIUM/HIGH/CRITICAL) so the
     * Notification Center can render a colour badge. Used by approval notifications.
     */
    public Notification notify(String type, String title, String body, String source, String actionId, String risk) {
        Notification saved = repository.save(new Notification(
                Ids.generate("ntf"),
                type, title, body, source, actionId, risk));
        // Heartbeat to your phone: mirror every notification to the private Discord channel (and Telegram
        // if that bridge is on). Both are dormant until configured, so this is a no-op by default.
        String line = "🔔 " + title + (body == null || body.isBlank() ? "" : "\n" + body);
        discord.push(line);
        if (telegram.pushNotifications()) {
            telegram.push(line);
        }
        return saved;
    }

    public List<Notification> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(limit, 200)));
    }

    public long unreadCount() {
        return repository.countByReadFalse();
    }

    @Transactional
    public void markRead(String id) {
        repository.findById(id).ifPresent(n -> {
            n.setRead(true);
            repository.save(n);
        });
    }

    @Transactional
    public void markAllRead() {
        repository.markAllRead();
    }
}
