package com.jarvis.connectors;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.jarvis.common.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Real Telegram connector — a capability Jarvis uses (via {@code connector_invoke}) to send and read
 * messages through the Telegram Bot API, using a bot token from the Secrets Vault (entry
 * {@code telegram-bot-token}). This is NOT a remote-control bridge (phone control lives on Discord);
 * it's a normal connector like GitHub/Slack — send a message, read recent updates, check the bot.
 */
@Component
@RequiredArgsConstructor
public class TelegramConnector implements Connector {

    private final RestClient client = RestClient.create("https://api.telegram.org");
    private final ObjectMapper mapper;

    @Override public String id() { return "telegram"; }
    @Override public String name() { return "Telegram"; }
    @Override public String category() { return "Messaging"; }
    @Override public String requiredSecret() { return "telegram-bot-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("get_me", "Bot identity", "Check the connected bot {} "),
                new ConnectorAction("send_message", "Send a message", "Send a message {chat_id, text}"),
                new ConnectorAction("get_updates", "Read recent messages", "Read recent inbound messages {offset?, limit?}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "get_me" -> getMe(token);
            case "send_message" -> sendMessage(a, token);
            case "get_updates" -> getUpdates(a, token);
            default -> throw new NotFoundException("Unknown Telegram action '" + actionId + "'");
        };
    }

    private String getMe(String token) throws Exception {
        JsonNode r = mapper.readTree(get(token, "getMe")).path("result");
        return "Bot @" + r.path("username").asText("?") + " (" + r.path("first_name").asText("") + ")";
    }

    private String sendMessage(JsonNode a, String token) throws Exception {
        String chatId = a.path("chat_id").asText(a.path("chat").asText(""));
        String text = a.path("text").asText(a.path("message").asText(""));
        if (chatId.isBlank()) {
            return "Error: provide 'chat_id' (the recipient chat).";
        }
        if (text.isBlank()) {
            return "Error: provide 'text' to send.";
        }
        var body = mapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("text", text);
        JsonNode r = mapper.readTree(post(token, "sendMessage", body.toString()));
        return r.path("ok").asBoolean() ? "Message sent to chat " + chatId + "." : "Telegram error: " + r;
    }

    private String getUpdates(JsonNode a, String token) throws Exception {
        int limit = a.path("limit").asInt(10);
        long offset = a.path("offset").asLong(0);
        JsonNode arr = mapper.readTree(get(token, "getUpdates?limit=" + limit + (offset > 0 ? "&offset=" + offset : "")))
                .path("result");
        StringBuilder sb = new StringBuilder();
        for (JsonNode u : arr) {
            JsonNode msg = u.path("message");
            sb.append("[").append(u.path("update_id").asLong()).append("] ")
                    .append(msg.path("from").path("first_name").asText("?")).append(": ")
                    .append(msg.path("text").asText("")).append("\n");
        }
        return sb.length() == 0 ? "No recent messages." : sb.toString().trim();
    }

    // Build the path inline (don't template the method — getUpdates carries a query string that
    // URI-variable expansion would percent-encode).
    private String get(String token, String method) {
        return client.get().uri("/bot" + token + "/" + method).retrieve().body(String.class);
    }

    private String post(String token, String method, String jsonBody) {
        return client.post().uri("/bot" + token + "/" + method)
                .header("Content-Type", "application/json").body(jsonBody)
                .retrieve().body(String.class);
    }
}
