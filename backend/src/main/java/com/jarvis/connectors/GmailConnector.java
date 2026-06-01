package com.jarvis.connectors;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * Real Gmail connector — calls the Gmail REST API with a Google OAuth access
 * token from the Secrets Vault (entry {@code gmail-token}).
 *
 * <p>Note: obtaining/refreshing the Google OAuth token (the consent flow) is a
 * separate setup step; this connector uses whatever access token is stored.
 */
@Component
@RequiredArgsConstructor
public class GmailConnector implements Connector {

    private final RestClient client = RestClient.create("https://gmail.googleapis.com/gmail/v1");
    private final ObjectMapper mapper;


    @Override public String id() { return "gmail"; }
    @Override public String name() { return "Gmail"; }
    @Override public String category() { return "Productivity"; }
    @Override public String requiredSecret() { return "gmail-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(new ConnectorAction("list_recent", "List recent", "List recent emails (subject + from)"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        if (!"list_recent".equals(actionId)) {
            throw new NotFoundException("Unknown Gmail action '" + actionId + "'");
        }
        JsonNode list = mapper.readTree(get("/users/me/messages?maxResults=5", token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode m : list.path("messages")) {
            String id = m.path("id").asText();
            JsonNode msg = mapper.readTree(get(
                    "/users/me/messages/" + id + "?format=metadata&metadataHeaders=Subject&metadataHeaders=From", token));
            String subject = header(msg, "Subject");
            String from = header(msg, "From");
            sb.append("• ").append(subject).append("  —  ").append(from).append("\n");
        }
        return sb.isEmpty() ? "No recent emails." : sb.toString().trim();
    }

    private String header(JsonNode message, String name) {
        for (JsonNode h : message.path("payload").path("headers")) {
            if (h.path("name").asText().equalsIgnoreCase(name)) {
                return h.path("value").asText("");
            }
        }
        return "(no " + name + ")";
    }

    private String get(String uri, String token) {
        return client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve().body(String.class);
    }
}
