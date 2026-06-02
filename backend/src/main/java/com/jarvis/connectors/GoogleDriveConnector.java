package com.jarvis.connectors;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.oauth.OAuthService;

import lombok.RequiredArgsConstructor;

/**
 * Google Drive connector backed by the OAuth flow (spec §9 + §13). Unlike the
 * token-in-vault connectors, it gets a fresh access token from {@link OAuthService}
 * (auto-refreshed). Its vault key {@code oauth:google} drives the connected status,
 * so it shows CONNECTED only after the user authorizes via /oauth.
 */
@Component
@RequiredArgsConstructor
public class GoogleDriveConnector implements Connector {

    private final RestClient client = RestClient.create("https://www.googleapis.com");
    private final ObjectMapper mapper;
    private final OAuthService oauth;

    @Override public String id() { return "gdrive"; }
    @Override public String name() { return "Google Drive"; }
    @Override public String category() { return "Productivity"; }
    @Override public String requiredSecret() { return "oauth:google"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(new ConnectorAction("list_files", "List files", "List your most recently modified Drive files"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String credential) throws Exception {
        if (!"list_files".equals(actionId)) {
            throw new NotFoundException("Unknown Drive action '" + actionId + "'");
        }
        String token = oauth.accessToken("google"); // fresh, auto-refreshed — ignores the stored bundle passed in
        String raw = client.get()
                .uri(b -> b.path("/drive/v3/files")
                        .queryParam("pageSize", 10)
                        .queryParam("orderBy", "modifiedTime desc")
                        .queryParam("fields", "files(name,mimeType,modifiedTime)").build())
                .header("Authorization", "Bearer " + token)
                .retrieve().body(String.class);
        JsonNode files = mapper.readTree(raw).path("files");
        StringBuilder sb = new StringBuilder();
        for (JsonNode f : files) {
            sb.append("• ").append(f.path("name").asText())
                    .append(" (").append(f.path("mimeType").asText("?")).append(")\n");
        }
        return sb.length() == 0 ? "No files in Drive." : sb.toString().trim();
    }
}
