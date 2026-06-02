package com.jarvis.connectors;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Real Notion connector — calls the Notion API with an integration token from the
 * Secrets Vault (entry {@code notion-token}). Like the other key-gated connectors
 * it makes a genuine HTTP call (401s without a valid token).
 */
@Component
@RequiredArgsConstructor
public class NotionConnector implements Connector {

    private static final String NOTION_VERSION = "2022-06-28";
    private final RestClient client = RestClient.create("https://api.notion.com");
    private final ObjectMapper mapper;

    @Override public String id() { return "notion"; }
    @Override public String name() { return "Notion"; }
    @Override public String category() { return "Productivity"; }
    @Override public String requiredSecret() { return "notion-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(new ConnectorAction("search", "Search", "Search your Notion pages and databases (args: query?)"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        if (!"search".equals(actionId)) {
            throw new NotFoundException("Unknown Notion action '" + actionId + "'");
        }
        JsonNode args = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        Map<String, Object> body = Map.of("query", args.path("query").asText(""), "page_size", 10);
        String raw = client.post().uri("/v1/search")
                .header("Authorization", "Bearer " + token)
                .header("Notion-Version", NOTION_VERSION)
                .header("Content-Type", "application/json")
                .body(body).retrieve().body(String.class);

        JsonNode results = mapper.readTree(raw).path("results");
        StringBuilder sb = new StringBuilder();
        for (JsonNode r : results) {
            String type = r.path("object").asText("");
            String title = extractTitle(r);
            String url = r.path("url").asText("");
            sb.append("• [").append(type).append("] ").append(title.isBlank() ? "(untitled)" : title);
            if (!url.isBlank()) {
                sb.append("\n  ").append(url);
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? "No Notion results." : sb.toString().trim();
    }

    /** Notion titles live under a "title" rich-text array somewhere in properties. */
    private String extractTitle(JsonNode result) {
        JsonNode props = result.path("properties");
        for (JsonNode prop : props) {
            if ("title".equals(prop.path("type").asText())) {
                JsonNode arr = prop.path("title");
                if (arr.isArray() && !arr.isEmpty()) {
                    return arr.get(0).path("plain_text").asText("");
                }
            }
        }
        return "";
    }
}
