package com.jarvis.connectors;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * Real Slack connector — calls the Slack Web API with a bot/user token from the
 * Secrets Vault (entry {@code slack-token}). Posting a message is a real,
 * mutating call; in a later pass it will route through the Approval Center.
 */
@Component
@RequiredArgsConstructor
public class SlackConnector implements Connector {

    private final RestClient client = RestClient.create("https://slack.com/api");
    private final ObjectMapper mapper;


    @Override public String id() { return "slack"; }
    @Override public String name() { return "Slack"; }
    @Override public String category() { return "Communication"; }
    @Override public String requiredSecret() { return "slack-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_channels", "List channels", "List public channels"),
                new ConnectorAction("post_message", "Post message", "Post a message: {channel, text}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        return switch (actionId) {
            case "list_channels" -> listChannels(token);
            case "post_message" -> postMessage(argumentsJson, token);
            default -> throw new NotFoundException("Unknown Slack action '" + actionId + "'");
        };
    }

    private String listChannels(String token) throws Exception {
        JsonNode root = mapper.readTree(client.get()
                .uri("/conversations.list?limit=50&types=public_channel")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(String.class));
        requireOk(root);
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : root.path("channels")) {
            sb.append("#").append(c.path("name").asText()).append("\n");
        }
        return sb.isEmpty() ? "No channels." : sb.toString().trim();
    }

    private String postMessage(String argsJson, String token) throws Exception {
        JsonNode args = mapper.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
        String channel = args.path("channel").asText("");
        String text = args.path("text").asText("");
        JsonNode root = mapper.readTree(client.post()
                .uri("/chat.postMessage")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .body(Map.of("channel", channel, "text", text))
                .retrieve().body(String.class));
        requireOk(root);
        return "Posted to " + channel + ".";
    }

    private void requireOk(JsonNode root) {
        if (!root.path("ok").asBoolean(false)) {
            throw new IllegalStateException("Slack API error: " + root.path("error").asText("unknown"));
        }
    }
}
