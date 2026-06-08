package com.jarvis.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.VoiceService;
import com.jarvis.command.CommandEngine;
import com.jarvis.command.CommandResult;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * Inbound half of the private Discord control channel: a minimal Discord Gateway client (JDK WebSocket,
 * no external library). It connects, IDENTIFYs, heartbeats, and for every message YOU post in the
 * configured channel runs it through the SAME cognitive door as the HUD ({@link CommandEngine}) and
 * replies in the channel — so anything you can type in Jarvis you can text it from your phone.
 *
 * <p>Dormant unless a bot token + channel id are set. Message handling runs off the WebSocket thread so
 * a slow model call never stalls heartbeats. Reconnects (fresh IDENTIFY) on any drop.
 */
@Component
@RequiredArgsConstructor
public class DiscordGateway {

    private static final Logger log = LoggerFactory.getLogger(DiscordGateway.class);

    private final JarvisDiscordProperties props;
    private final DiscordService discord;
    private final CommandEngine commandEngine;
    private final ObjectMapper mapper;
    private final VoiceService voice;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(daemon("discord-gateway"));
    private final java.util.concurrent.ExecutorService workers =
            Executors.newSingleThreadExecutor(daemon("discord-worker"));

    private volatile WebSocket socket;
    private volatile Integer lastSeq;          // last sequence number (for heartbeats)
    private volatile String selfId;            // the bot's own user id (don't reply to ourselves)
    private volatile ScheduledFuture<?> heartbeat;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private volatile boolean shuttingDown = false;

    @PostConstruct
    void start() {
        if (!discord.active() || props.getChannelId() == null || props.getChannelId().isBlank()) {
            log.info("Discord control channel off (no token/channel) — dormant.");
            return;
        }
        log.info("Discord control channel connecting…");
        connect();
    }

    @PreDestroy
    void stop() {
        shuttingDown = true;
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
        scheduler.shutdownNow();
        workers.shutdownNow();
    }

    private void connect() {
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(props.getGatewayUrl()), new Handler())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        log.warn("Discord gateway connect failed: {}", err.getMessage());
                        scheduleReconnect();
                    } else {
                        this.socket = ws;
                    }
                });
    }

    private void scheduleReconnect() {
        if (shuttingDown || !reconnecting.compareAndSet(false, true)) {
            return;
        }
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
        scheduler.schedule(() -> {
            reconnecting.set(false);
            if (!shuttingDown) {
                log.info("Discord gateway reconnecting…");
                connect();
            }
        }, props.getReconnectBackoffMs(), TimeUnit.MILLISECONDS);
    }

    /** WebSocket callbacks. onText may arrive in fragments → accumulate until {@code last}. */
    private final class Handler implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String payload = buf.toString();
                buf.setLength(0);
                try {
                    onPayload(webSocket, payload);
                } catch (Exception e) {
                    log.debug("Discord payload handling failed: {}", e.getMessage());
                }
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("Discord gateway closed ({}): {}", statusCode, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("Discord gateway error: {}", error.getMessage());
            scheduleReconnect();
        }
    }

    private void onPayload(WebSocket ws, String raw) throws Exception {
        JsonNode root = mapper.readTree(raw);
        if (root.hasNonNull("s")) {
            lastSeq = root.get("s").asInt();
        }
        int op = root.path("op").asInt(-1);
        switch (op) {
            case 10 -> {   // HELLO → start heartbeats + identify
                long interval = root.path("d").path("heartbeat_interval").asLong(41250);
                startHeartbeat(ws, interval);
                identify(ws);
            }
            case 0 -> dispatch(root);                       // event
            case 1 -> sendHeartbeat(ws);                    // server asked us to heartbeat now
            case 7, 9 -> scheduleReconnect();               // reconnect / invalid session
            default -> { /* 11 = heartbeat ACK, etc. */ }
        }
    }

    private void dispatch(JsonNode root) {
        String t = root.path("t").asText("");
        JsonNode d = root.path("d");
        if ("READY".equals(t)) {
            selfId = d.path("user").path("id").asText("");
            String botName = d.path("user").path("username").asText("?");
            log.info("Discord gateway READY — connected as @{}; listening on channel {}.", botName, props.getChannelId());
        } else if ("MESSAGE_CREATE".equals(t)) {
            onMessage(d);
        }
    }

    private void onMessage(JsonNode msg) {
        String channelId = msg.path("channel_id").asText("");
        String authorId = msg.path("author").path("id").asText("");
        boolean isBot = msg.path("author").path("bot").asBoolean(false);
        String content = msg.path("content").asText("").trim();
        log.info("Discord MESSAGE_CREATE: channel={} (configured={}), bot={}, contentLen={}",
                channelId, props.getChannelId(), isBot, content.length());
        if (!channelId.equals(props.getChannelId()) || isBot || authorId.equals(selfId)) {
            return;   // only the owner's messages in the one private channel
        }
        // A typed message wins; otherwise, if voice-notes are enabled, transcribe an audio attachment.
        String voiceText = content.isEmpty() && props.isVoiceNotes() ? transcribeVoiceNote(msg) : null;
        boolean fromVoice = content.isEmpty() && voiceText != null;
        String input = content.isEmpty() ? voiceText : content;
        if (input == null || input.isBlank()) {
            return;   // nothing actionable (no text, and no voice note to transcribe)
        }
        // Run off the WS thread (a model call can take seconds) so heartbeats keep flowing.
        workers.submit(() -> {
            try {
                // If transcription failed (no key / fetch error), report that instead of feeding it to the brain.
                if (fromVoice && isTranscriptionFailure(input)) {
                    discord.send(channelId, "🎙️ " + input);
                    return;
                }
                CommandResult r = commandEngine.execute(input, "discord");
                String body = reply(r);
                if (fromVoice) {
                    body = "🎙️ \"" + input + "\"\n\n" + body;   // echo what I heard, then the answer
                }
                discord.send(channelId, body);
            } catch (Exception e) {
                discord.send(channelId, "⚠️ I hit an error handling that: " + e.getMessage());
            }
        });
    }

    /** Download the first audio attachment (a Discord voice note) and transcribe it; null if there's none. */
    private String transcribeVoiceNote(JsonNode msg) {
        for (JsonNode att : msg.path("attachments")) {
            String url = att.path("url").asText("");
            String ct = att.path("content_type").asText("");
            String fn = att.path("filename").asText("voice-note");
            boolean isAudio = ct.startsWith("audio/")
                    || fn.toLowerCase().matches(".*\\.(ogg|mp3|m4a|wav|webm|mp4|mpeg|mpga|flac)$");
            if (url.isBlank() || !isAudio) {
                continue;
            }
            try {
                byte[] bytes = httpClient.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()).body();
                return voice.transcribe(bytes, fn);
            } catch (Exception e) {
                return "Couldn't fetch the voice note: " + e.getMessage();
            }
        }
        return null;
    }

    private static boolean isTranscriptionFailure(String t) {
        return t.startsWith("Error") || t.startsWith("Couldn't") || t.contains("needs an OpenAI API key");
    }

    private static String reply(CommandResult r) {
        if (r == null) {
            return "(no response)";
        }
        if (r.status() == CommandResult.Status.ERROR) {
            return "⚠️ " + (r.message() == null ? "Something went wrong." : r.message());
        }
        String msg = r.message() == null || r.message().isBlank() ? "(done)" : r.message();
        if (r.data() instanceof String s && !s.isBlank() && !s.equals(msg)) {
            msg = msg + "\n" + s;
        }
        return msg;
    }

    private void startHeartbeat(WebSocket ws, long intervalMs) {
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
        heartbeat = scheduler.scheduleAtFixedRate(() -> sendHeartbeat(ws), intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat(WebSocket ws) {
        try {
            ws.sendText("{\"op\":1,\"d\":" + (lastSeq == null ? "null" : lastSeq) + "}", true);
        } catch (Exception e) {
            log.debug("Discord heartbeat failed: {}", e.getMessage());
        }
    }

    private void identify(WebSocket ws) {
        var d = mapper.createObjectNode();
        d.put("token", props.getBotToken());
        d.put("intents", props.getIntents());
        var p = d.putObject("properties");
        p.put("os", "linux");
        p.put("browser", "jarvis");
        p.put("device", "jarvis");
        var identify = mapper.createObjectNode();
        identify.put("op", 2);
        identify.set("d", d);
        ws.sendText(identify.toString(), true);
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
