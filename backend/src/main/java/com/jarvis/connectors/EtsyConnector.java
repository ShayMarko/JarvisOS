package com.jarvis.connectors;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Real Etsy connector (Open API v3) — sell handmade/printable/POD products on Etsy. Etsy needs TWO creds:
 * an OAuth access token AND the app keystring (x-api-key). Store both in the one Secrets Vault entry
 * {@code etsy-token} as {@code <access_token>|<api_key>} (pipe-separated); read-only calls use just the
 * api key, writes use both. create_draft_listing is approval-gated and creates a DRAFT (you review + publish).
 */
@Component
@RequiredArgsConstructor
public class EtsyConnector implements Connector {

    private static final String BASE = "https://openapi.etsy.com";

    private final RestClient client = RestClient.create(BASE);
    private final ObjectMapper mapper;

    @Override public String id() { return "etsy"; }
    @Override public String name() { return "Etsy"; }
    @Override public String category() { return "Commerce"; }
    @Override public String requiredSecret() { return "etsy-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return "create_draft_listing".equals(actionId)
                ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("find_shop", "Find shop", "Look up a shop + its shopId {shopName}"),
                new ConnectorAction("list_listings", "List listings", "Active listings for a shop {shopId}"),
                new ConnectorAction("create_draft_listing", "Create draft listing",
                        "Create a DRAFT listing {shopId, title, description, price, taxonomyId, quantity?} — review + publish on Etsy"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String credential) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        String[] parts = (credential == null ? "" : credential).split("\\|", 2);
        String accessToken = parts.length > 0 ? parts[0].trim() : "";
        String apiKey = parts.length > 1 ? parts[1].trim() : "";
        if (apiKey.isBlank()) {
            return "Etsy needs both creds. Store the \"etsy-token\" vault entry as <access_token>|<api_key>.";
        }
        return switch (actionId) {
            case "find_shop" -> findShop(a, apiKey);
            case "list_listings" -> listListings(a, apiKey);
            case "create_draft_listing" -> createDraft(a, accessToken, apiKey);
            default -> throw new NotFoundException("Unknown Etsy action '" + actionId + "'");
        };
    }

    private String findShop(JsonNode a, String apiKey) {
        String name = a.path("shopName").asText("");
        if (name.isBlank()) {
            return "Provide 'shopName'.";
        }
        JsonNode r = read(client.get().uri("/v3/application/shops?shop_name=" + enc(name))
                .header("x-api-key", apiKey).retrieve().body(String.class)).path("results");
        StringBuilder sb = new StringBuilder();
        for (JsonNode s : r) {
            sb.append("• ").append(s.path("shop_name").asText()).append(" — shopId=")
              .append(s.path("shop_id").asLong()).append('\n');
        }
        return sb.isEmpty() ? "No shop matched \"" + name + "\"." : sb.toString().strip();
    }

    private String listListings(JsonNode a, String apiKey) {
        long shopId = a.path("shopId").asLong(0);
        if (shopId <= 0) {
            return "Provide 'shopId' (from find_shop).";
        }
        JsonNode r = read(client.get().uri("/v3/application/shops/{id}/listings/active?limit=25", shopId)
                .header("x-api-key", apiKey).retrieve().body(String.class)).path("results");
        StringBuilder sb = new StringBuilder();
        for (JsonNode l : r) {
            sb.append("• ").append(l.path("title").asText()).append(" — $")
              .append(l.path("price").path("amount").asDouble(0) / l.path("price").path("divisor").asDouble(100))
              .append('\n');
        }
        return sb.isEmpty() ? "No active listings." : sb.toString().strip();
    }

    private String createDraft(JsonNode a, String accessToken, String apiKey) {
        long shopId = a.path("shopId").asLong(0);
        String title = a.path("title").asText("");
        String description = a.path("description").asText("");
        long taxonomyId = a.path("taxonomyId").asLong(0);
        if (shopId <= 0 || title.isBlank() || description.isBlank() || taxonomyId <= 0) {
            return "Provide 'shopId', 'title', 'description' and 'taxonomyId' (Etsy requires a taxonomy id).";
        }
        if (accessToken.isBlank()) {
            return "Creating a listing needs the OAuth access token half of the etsy-token (<access_token>|<api_key>).";
        }
        double price = a.path("price").asDouble(0);
        int quantity = a.path("quantity").asInt(1);
        String form = "quantity=" + quantity
                + "&title=" + enc(title)
                + "&description=" + enc(description)
                + "&price=" + price
                + "&who_made=i_did"
                + "&when_made=made_to_order"
                + "&taxonomy_id=" + taxonomyId
                + "&type=physical"
                + "&state=draft";
        JsonNode r = read(client.post().uri("/v3/application/shops/{id}/listings", shopId)
                .header("Authorization", "Bearer " + accessToken)
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(String.class));
        long listingId = r.path("listing_id").asLong(0);
        if (listingId <= 0) {
            return "Etsy rejected the listing: " + r;
        }
        return "✅ Created DRAFT Etsy listing \"" + title + "\" (id=" + listingId
                + "). Add photos + publish on Etsy.";
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
