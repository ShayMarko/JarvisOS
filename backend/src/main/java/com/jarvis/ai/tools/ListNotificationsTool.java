package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.notification.Notification;
import com.jarvis.notification.NotificationService;

import lombok.RequiredArgsConstructor;

/** Reads recent notifications — so "what notifications / any alerts" works over Discord/voice/chat. */
@Component
@RequiredArgsConstructor
public class ListNotificationsTool implements Tool {

    private final NotificationService notifications;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_notifications",
                "List recent notifications/alerts (title, body, type, read/unread). Use when asked "
                + "'what notifications do I have', 'any alerts', 'show my notifications'. Optional 'limit' (default 10).",
                "{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\"}}}");
    }

    @Override
    public String execute(String argumentsJson) {
        int limit = Math.max(1, Math.min(50, ToolArgs.intVal(mapper, argumentsJson, "limit", 10)));
        List<Notification> recent = notifications.recent(limit);
        if (recent.isEmpty()) {
            return "No notifications — you're all caught up.";
        }
        long unread = recent.stream().filter(n -> !n.isRead()).count();
        StringBuilder sb = new StringBuilder("🔔 Notifications (" + unread + " unread of " + recent.size() + "):\n");
        for (Notification n : recent) {
            sb.append(n.isRead() ? "• " : "• ● ").append('[').append(n.getType()).append("] ").append(n.getTitle());
            if (n.getBody() != null && !n.getBody().isBlank()) {
                sb.append(" — ").append(n.getBody());
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }
}
