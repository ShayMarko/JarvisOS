package com.jarvis.discord;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Outbound half of the Discord channel: send a message / push a notification to the configured private
 * channel via the Discord REST API. Inert unless a bot token is set. The inbound (listen for your
 * messages) half is {@link DiscordGateway}.
 */
@Service
public class DiscordService {

    private static final Logger log = LoggerFactory.getLogger(DiscordService.class);

    private final DiscordProperties props;
    private final RestClient http;

    public DiscordService(DiscordProperties props) {
        this.props = props;
        this.http = RestClient.builder().baseUrl(props.getApiBaseUrl()).build();
    }

    /** On when the channel is enabled AND a token is present. */
    public boolean active() {
        return props.isEnabled() && notBlank(props.getBotToken());
    }

    /** Whether to mirror notifications/briefings to the channel. */
    public boolean pushNotifications() {
        return active() && props.isPushNotifications() && notBlank(props.getChannelId());
    }

    /** Send a message to a specific channel. No-op (logged) when inert or on error. */
    public void send(String channelId, String text) {
        if (!active() || channelId == null || channelId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        try {
            http.post().uri("/channels/{c}/messages", channelId)
                    .header("Authorization", "Bot " + props.getBotToken())
                    .header("Content-Type", "application/json")
                    .body(Map.of("content", clip(text)))
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("Discord send failed: {}", e.getMessage());
        }
    }

    /** Push to the owner's configured private channel (used for notifications + the daily briefing). */
    public void push(String text) {
        if (pushNotifications()) {
            send(props.getChannelId(), text);
        }
    }

    private String clip(String s) {
        int max = props.getMaxMessageChars();
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
