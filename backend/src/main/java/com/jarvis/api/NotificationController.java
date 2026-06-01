package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.notification.Notification;
import com.jarvis.notification.NotificationService;

/** Notification Center endpoints (spec §8). */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;


    @GetMapping
    public List<Notification> list(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return notifications.recent(limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable String id) {
        notifications.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll() {
        notifications.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
