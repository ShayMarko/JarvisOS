package com.jarvis.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.discord} — the private 2-way remote-control channel (the headless Mac mini lives
 * in a closet; this is how Shay commands it from his phone). Dormant until a bot token + channel id are
 * set, so it never connects on a default install.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.discord")
public class JarvisDiscordProperties {

    /** Master switch. Even when true, the gateway stays off unless a botToken + channelId are present. */
    private boolean enabled = false;
    /** Bot token (Discord Developer Portal → Bot). Secret → set via env. Needs the MESSAGE CONTENT intent on. */
    private String botToken = "";
    /** The ONE private channel id Jarvis listens to and replies in (your private server's channel). */
    private String channelId = "";
    /** Mirror every Notification Center event (and the daily briefing) to the channel. */
    private boolean pushNotifications = true;

    // --- Tunables ---
    private String apiBaseUrl = "https://discord.com/api/v10";
    private String gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json";
    /** Gateway intents: GUILD_MESSAGES(512) + DIRECT_MESSAGES(4096) + MESSAGE_CONTENT(32768). */
    private int intents = 37376;
    /** Max characters per outbound message (Discord hard-caps at 2000). */
    private int maxMessageChars = 1900;
    /** Delay before reconnecting after a dropped gateway connection (ms). */
    private long reconnectBackoffMs = 5000;
}
