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
        mirror("🔔 " + title + (body == null || body.isBlank() ? "" : "\n" + body));
        return saved;
    }

    /**
     * Surface an approval as a full, self-contained decision packet — what Jarvis wants to do, the action
     * type, the risk, why (description), a preview of the payload, and the id to reply with. The web
     * Notification keeps a concise body (the bell renders the detailed card from the live request); the
     * Discord/Telegram push gets the whole packet so you can decide remotely with everything in front of you.
     */
    public Notification notifyApproval(String title, String actionType, String description,
                                       String risk, String preview, String requestId) {
        Notification saved = repository.save(new Notification(
                Ids.generate("ntf"), "warning", "Approval needed",
                title + (risk == null || risk.isBlank() ? "" : " (risk " + risk + ")"),
                "approval", requestId, risk));

        StringBuilder sb = new StringBuilder("🔐 Approval needed");
        if (risk != null && !risk.isBlank()) sb.append(" · risk ").append(risk);
        sb.append('\n').append(title);
        if (actionType != null && !actionType.isBlank()) sb.append("\n• Action: ").append(actionType);
        if (description != null && !description.isBlank()) sb.append("\n• Why: ").append(description);
        if (preview != null && !preview.isBlank()) {
            String p = preview.length() > 600 ? preview.substring(0, 600) + "…" : preview;
            sb.append("\n• Preview:\n").append(p);
        }
        sb.append("\n→ reply  `/approve ").append(requestId).append("`  or  `/decline ").append(requestId).append('`');
        mirror(sb.toString());
        return saved;
    }

    /** Push a line to the private Discord channel and (if enabled) Telegram. No-op until configured. */
    private void mirror(String line) {
        discord.push(line);
        if (telegram.pushNotifications()) {
            telegram.push(line);
        }
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
