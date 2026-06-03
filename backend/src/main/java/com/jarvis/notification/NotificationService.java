package com.jarvis.notification;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jarvis.telegram.TelegramService;

/** The Notification Center (spec §8). Other subsystems call {@link #notify} to surface events. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final TelegramService telegram;


    public Notification notify(String type, String title, String body, String source) {
        Notification saved = repository.save(new Notification(
                "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                type, title, body, source));
        // Heartbeat to your phone: mirror every notification to Telegram when the bridge is on.
        if (telegram.pushNotifications()) {
            telegram.push("🔔 " + title + (body == null || body.isBlank() ? "" : "\n" + body));
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
