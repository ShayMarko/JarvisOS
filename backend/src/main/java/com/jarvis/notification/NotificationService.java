package com.jarvis.notification;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Notification Center (spec §8). Other subsystems call {@link #notify} to surface events. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;


    public Notification notify(String type, String title, String body, String source) {
        return repository.save(new Notification(
                "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                type, title, body, source));
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
