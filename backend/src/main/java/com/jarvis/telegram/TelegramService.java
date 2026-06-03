package com.jarvis.telegram;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin Telegram Bot API client — the "mouth" that lets you reach a headless Jarvis from your phone.
 * Send replies, push proactive notifications, and long-poll for inbound messages. All calls go to
 * api.telegram.org over HTTPS (no inbound port opened on the Mac), so it's safe for a closet machine.
 * Inert unless {@link TelegramProperties} has a bot token.
 */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final TelegramProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    /** One inbound message: which Telegram update it was, who sent it, and the text. */
    public record Update(long updateId, String chatId, String text) {}

    public TelegramService(TelegramProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(props.getConnectTimeoutSeconds()));
        rf.setReadTimeout(Duration.ofSeconds(props.getReadTimeoutSeconds()));   // must exceed longPollSeconds
        this.http = RestClient.builder().requestFactory(rf)
                .baseUrl(props.getApiBaseUrl())
                .defaultHeader("content-type", "application/json")
                .build();
    }

    /** True when the bridge is switched on AND a token is present. */
    public boolean active() {
        return props.isEnabled() && props.getBotToken() != null && !props.getBotToken().isBlank();
    }

    public boolean pollingEnabled() {
        return active() && props.isPolling();
    }

    /** Send a message to a specific chat. */
    public void send(String chatId, String text) {
        if (!active() || chatId == null || chatId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        try {
            http.post().uri("/bot{t}/sendMessage", props.getBotToken())
                    .body(Map.of("chat_id", chatId, "text", clip(text)))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Telegram sendMessage failed: {}", e.getMessage());
        }
    }

    /** Proactively push to the configured owner chat (notifications, digest). No-op if no chatId set. */
    public void push(String text) {
        if (active() && props.getChatId() != null && !props.getChatId().isBlank()) {
            send(props.getChatId(), text);
        }
    }

    public boolean pushNotifications() {
        return active() && props.isPushNotifications();
    }

    /** Long-poll for new messages since {@code offset}. Returns text messages only. */
    public List<Update> poll(long offset) {
        List<Update> out = new ArrayList<>();
        if (!pollingEnabled()) {
            return out;
        }
        try {
            String raw = http.get()
                    .uri("/bot{t}/getUpdates?timeout={s}&allowed_updates=[\"message\"]&offset={o}",
                            props.getBotToken(), props.getLongPollSeconds(), offset)
                    .retrieve().body(String.class);
            JsonNode root = mapper.readTree(raw == null ? "{}" : raw);
            for (JsonNode upd : root.path("result")) {
                long id = upd.path("update_id").asLong();
                JsonNode msg = upd.path("message");
                String text = msg.path("text").asText("");
                String chat = msg.path("chat").path("id").asText("");
                if (!text.isBlank() && !chat.isBlank()) {
                    out.add(new Update(id, chat, text));
                }
            }
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.debug("Telegram poll failed (will retry): {}", e.getMessage());
        }
        return out;
    }

    /** Pause between empty long-polls (ms). */
    public long idlePollMs() {
        return props.getIdlePollMs();
    }

    /** Back-off after a poll error (ms). */
    public long errorBackoffMs() {
        return props.getErrorBackoffMs();
    }

    /** Telegram caps messages at 4096 chars; trim to the configured max. */
    private String clip(String s) {
        int max = props.getMaxMessageChars();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
