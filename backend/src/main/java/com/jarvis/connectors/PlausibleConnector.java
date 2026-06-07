package com.jarvis.connectors;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Real Plausible Analytics connector — gives the Analyst REAL traffic numbers instead of flying blind.
 * Privacy-friendly, simple API. Token from the Secrets Vault (entry {@code plausible-token}). Reads
 * aggregate stats, top pages and top sources for a site — the inputs an A/B test or a "double-down" call needs.
 */
@Component
@RequiredArgsConstructor
public class PlausibleConnector implements Connector {

    private final RestClient client = RestClient.create("https://plausible.io");
    private final ObjectMapper mapper;

    @Override public String id() { return "plausible"; }
    @Override public String name() { return "Plausible Analytics"; }
    @Override public String category() { return "Analytics"; }
    @Override public String requiredSecret() { return "plausible-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("stats", "Site stats", "Visitors/pageviews/bounce {site, period?=30d}"),
                new ConnectorAction("top_pages", "Top pages", "Most-visited pages {site, period?=30d}"),
                new ConnectorAction("top_sources", "Top sources", "Where traffic comes from {site, period?=30d}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        String site = a.path("site").asText("");
        if (site.isBlank()) {
            return "Provide 'site' (the Plausible site_id / domain).";
        }
        String period = a.path("period").asText("30d");
        return switch (actionId) {
            case "stats" -> stats(site, period, token);
            case "top_pages" -> breakdown(site, period, "event:page", "Top pages", token);
            case "top_sources" -> breakdown(site, period, "visit:source", "Top sources", token);
            default -> throw new NotFoundException("Unknown Plausible action '" + actionId + "'");
        };
    }

    private String stats(String site, String period, String token) {
        JsonNode r = read(get("/api/v1/stats/aggregate?site_id=" + enc(site) + "&period=" + enc(period)
                + "&metrics=visitors,pageviews,bounce_rate,visit_duration", token)).path("results");
        return "📊 " + site + " (" + period + "): "
                + r.path("visitors").path("value").asInt() + " visitors, "
                + r.path("pageviews").path("value").asInt() + " pageviews, "
                + r.path("bounce_rate").path("value").asInt() + "% bounce, "
                + r.path("visit_duration").path("value").asInt() + "s avg visit.";
    }

    private String breakdown(String site, String period, String property, String label, String token) {
        JsonNode r = read(get("/api/v1/stats/breakdown?site_id=" + enc(site) + "&period=" + enc(period)
                + "&property=" + enc(property) + "&limit=10", token)).path("results");
        StringBuilder sb = new StringBuilder(label).append(" — ").append(site).append(" (").append(period).append("):\n");
        String key = property.substring(property.indexOf(':') + 1);
        boolean any = false;
        for (JsonNode row : r) {
            any = true;
            sb.append("• ").append(row.path(key).asText()).append(" — ")
              .append(row.path("visitors").asInt()).append(" visitors\n");
        }
        return any ? sb.toString().strip() : "No data for " + site + " in " + period + ".";
    }

    private String get(String path, String token) {
        return client.get().uri(path).header("Authorization", "Bearer " + token).retrieve().body(String.class);
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
