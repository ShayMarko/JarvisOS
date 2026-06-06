package com.jarvis.connectors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/**
 * Real Netlify connector — the "deploy/host" step of the money loop for STATIC output (SEO sites, landing
 * pages). Creates a site and deploys a zip of static files (index.html at the zip root). Token from the
 * Secrets Vault (entry {@code netlify-token}). Deploying is a real external action — worth gating behind
 * approval. (Micro-APIs need a server host like Fly/Render — a separate future connector.)
 */
@Component
@RequiredArgsConstructor
public class NetlifyConnector implements Connector {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final RestClient client = RestClient.create("https://api.netlify.com/api/v1");
    private final ObjectMapper mapper;
    private final FileSystemService fs;

    @Override public String id() { return "netlify"; }
    @Override public String name() { return "Netlify"; }
    @Override public String category() { return "Hosting"; }
    @Override public String requiredSecret() { return "netlify-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_sites", "List sites", "Your Netlify sites + URLs"),
                new ConnectorAction("create_site", "Create a site", "Create a new site {name?}"),
                new ConnectorAction("deploy_zip", "Deploy a zip",
                        "Deploy a zip of static files to a site {siteId, zipPath} (index.html at the zip root)"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "list_sites" -> listSites(token);
            case "create_site" -> createSite(a, token);
            case "deploy_zip" -> deployZip(a, token);
            default -> throw new NotFoundException("Unknown Netlify action '" + actionId + "'");
        };
    }

    private String listSites(String token) {
        JsonNode arr = read(client.get().uri("/sites").header("Authorization", "Bearer " + token)
                .retrieve().body(String.class));
        StringBuilder sb = new StringBuilder();
        for (JsonNode s : arr) {
            sb.append("• ").append(s.path("name").asText()).append(" — ")
              .append(s.path("ssl_url").asText(s.path("url").asText("?"))).append('\n');
        }
        return sb.isEmpty() ? "No Netlify sites yet." : sb.toString().strip();
    }

    private String createSite(JsonNode a, String token) {
        String name = a.path("name").asText("");
        String body = name.isBlank() ? "{}" : "{\"name\":\"" + name.replaceAll("[^a-zA-Z0-9-]", "-") + "\"}";
        JsonNode s = read(client.post().uri("/sites").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        return "✅ Created Netlify site \"" + s.path("name").asText() + "\" (id " + s.path("id").asText()
                + ") → " + s.path("ssl_url").asText(s.path("url").asText("(pending)"));
    }

    private String deployZip(JsonNode a, String token) throws Exception {
        String siteId = a.path("siteId").asText(a.path("site_id").asText(""));
        String zipPath = a.path("zipPath").asText(a.path("path").asText(""));
        if (siteId.isBlank() || zipPath.isBlank()) {
            return "Provide 'siteId' and 'zipPath' (a zip of the static files, index.html at root).";
        }
        Path zip = fs.resolveExisting(zipPath);
        byte[] bytes = Files.readAllBytes(zip);
        JsonNode d = read(client.post().uri("/sites/{id}/deploys", siteId)
                .header("Authorization", "Bearer " + token)
                .contentType(ZIP).body(bytes).retrieve().body(String.class));
        return "🚀 Deploy started (state " + d.path("state").asText("?") + ") → "
                + d.path("ssl_url").asText(d.path("deploy_ssl_url").asText("(URL pending)"));
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "[]" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
