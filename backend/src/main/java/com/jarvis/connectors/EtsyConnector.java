package com.jarvis.connectors;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;


/**
 * Real Etsy connector (Open API v3) — sell handmade/printable/POD products on Etsy. Etsy needs TWO creds:
 * an OAuth access token AND the app keystring (x-api-key). Store both in the one Secrets Vault entry
 * {@code etsy-token} as {@code <access_token>|<api_key>} (pipe-separated); read-only calls use just the
 * api key, writes use both. create_draft_listing is approval-gated and creates a DRAFT (you review + publish).
 */
@Component
public class EtsyConnector extends AbstractRestConnector {

    public EtsyConnector(ObjectMapper mapper) {
        super(mapper);
    }

    private static final String BASE = "https://openapi.etsy.com";

    private final RestClient client = RestClient.create(BASE);
    private final RestClient authClient = RestClient.create("https://api.etsy.com");   // OAuth token host
    // Cached short-lived access token, refreshed from the long-lived (90-day) refresh token on demand.
    private volatile String cachedAccessToken;
    private volatile long cachedExpiryEpoch;

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
        // Vault entry "etsy-token" = <refresh_token>|<keystring>  (optionally |<shared_secret>).
        // The keystring is both the OAuth client_id AND the x-api-key. The refresh token (90-day) is
        // exchanged for a fresh 1-hour access token on demand — no more manual token babysitting.
        String[] parts = (credential == null ? "" : credential).split("\\|", 3);
        String refreshToken = parts.length > 0 ? parts[0].trim() : "";
        String apiKey = parts.length > 1 ? parts[1].trim() : "";
        String clientSecret = parts.length > 2 ? parts[2].trim() : "";
        if (refreshToken.isBlank() || apiKey.isBlank()) {
            return "Store the \"etsy-token\" vault entry as <refresh_token>|<keystring> (optionally |<shared_secret>). "
                    + "Get the initial refresh token once via Etsy's OAuth (PKCE) consent; Jarvis refreshes it after.";
        }
        return switch (actionId) {
            case "find_shop" -> findShop(a, apiKey);                                    // x-api-key only
            case "list_listings" -> listListings(a, apiKey);                            // x-api-key only
            case "create_draft_listing" -> createDraft(a, accessToken(refreshToken, apiKey, clientSecret), apiKey);
            default -> throw new NotFoundException("Unknown Etsy action '" + actionId + "'");
        };
    }

    /** Exchange the long-lived refresh token for a fresh access token (cached in-memory until ~expiry). */
    private synchronized String accessToken(String refreshToken, String apiKey, String clientSecret) {
        long now = java.time.Instant.now().getEpochSecond();
        if (cachedAccessToken != null && now < cachedExpiryEpoch - 60) {
            return cachedAccessToken;
        }
        StringBuilder form = new StringBuilder("grant_type=refresh_token")
                .append("&client_id=").append(enc(apiKey))
                .append("&refresh_token=").append(enc(refreshToken));
        if (!clientSecret.isBlank()) {
            form.append("&client_secret=").append(enc(clientSecret));
        }
        JsonNode r = read(authClient.post().uri("/v3/public/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form.toString())
                .retrieve().body(String.class));
        String at = r.path("access_token").asText("");
        if (at.isBlank()) {
            throw new com.jarvis.error.Exceptions.ConflictException(
                    "Etsy token refresh failed — re-authorize (refresh tokens expire after 90 days). " + r);
        }
        cachedAccessToken = at;
        cachedExpiryEpoch = now + r.path("expires_in").asLong(3600);
        return at;
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
        double price = a.path("price").asDouble(0);
        int quantity = a.path("quantity").asInt(1);
        String form = "quantity=" + quantity
                + "&title=" + enc(title)
                + "&description=" + enc(description)
                + "&price=" + price
                + "&who_made=i_did"
                + "&when_made=made_to_order"
                + "&taxonomy_id=" + taxonomyId
                + "&type=physical";
        // NOTE: no 'state' param — createDraftListing ALWAYS creates a draft; 'state' (active/inactive)
        // is an UPDATE-only field, and Etsy's strict request validation 400s on unexpected params.
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


}
