package com.jarvis.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.telegram} — the optional phone bridge. Dormant until a BotFather token is set,
 * so it never runs (and never reaches the network) on a default install.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.telegram")
public class JarvisTelegramProperties {

    /** Master switch. Even when true, the bridge stays off unless a botToken is present. */
    private boolean enabled = false;
    /** BotFather token (get one by messaging @BotFather → /newbot). Secret → set via env. */
    private String botToken = "";
    /** The owner's chat id — where proactive pushes (notifications/digest) are sent. Optional;
     *  if blank, Jarvis still replies to whoever messages it but won't push unsolicited. */
    private String chatId = "";
    /** Whether to poll for inbound messages (the "text Jarvis from your phone" path). */
    private boolean polling = true;
    /** Push every Notification Center event to the owner's phone. */
    private boolean pushNotifications = true;

    // --- Tunables (were hard-coded constants; now configurable) ----------------------------------
    /** Telegram Bot API base URL. */
    private String apiBaseUrl = "https://api.telegram.org";
    /** Long-poll hold time per getUpdates call (seconds). */
    private int longPollSeconds = 50;
    /** HTTP read timeout (seconds) — must exceed longPollSeconds. */
    private int readTimeoutSeconds = 65;
    /** HTTP connect timeout (seconds). */
    private int connectTimeoutSeconds = 5;
    /** Max characters per outbound message (Telegram hard-caps at 4096). */
    private int maxMessageChars = 4000;
    /** Pause between empty long-polls (ms). */
    private long idlePollMs = 1000;
    /** Back-off after a poll error (ms). */
    private long errorBackoffMs = 3000;
}
