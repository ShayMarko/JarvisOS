package com.jarvis.connectors;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * Lemon Squeezy — a MERCHANT-OF-RECORD store for the money lanes: it collects sales tax / VAT for you, which
 * Stripe/Gumroad don't, so it's the lower-friction way to sell digital products internationally. Secret from
 * the Secrets Vault (entry {@code lemonsqueezy-token}). Uses the JSON:API format Lemon Squeezy requires.
 * Reads stores/products/revenue, and creates a hosted checkout link for a variant (approval-gated).
 */
@Component
public class LemonSqueezyConnector extends AbstractRestConnector {

    private static final String JSON_API = "application/vnd.api+json";

    public LemonSqueezyConnector(ObjectMapper mapper) {
        super(mapper);
    }

    private final RestClient client = RestClient.create("https://api.lemonsqueezy.com");

    @Override public String id() { return "lemonsqueezy"; }
    @Override public String name() { return "Lemon Squeezy"; }
    @Override public String category() { return "Commerce"; }
    @Override public String requiredSecret() { return "lemonsqueezy-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return "create_checkout".equals(actionId)
                ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_stores", "List stores", "Your Lemon Squeezy stores (+ ids)"),
                new ConnectorAction("list_products", "List products", "Products across your stores"),
                new ConnectorAction("recent_revenue", "Recent revenue", "Sum of recent orders (feeds ROI)"),
                new ConnectorAction("create_checkout", "Create checkout link",
                        "Create a hosted checkout {storeId, variantId} → a sellable URL (tax handled for you)"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String key) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "list_stores" -> listStores(key);
            case "list_products" -> listProducts(key);
            case "recent_revenue" -> recentRevenue(key);
            case "create_checkout" -> createCheckout(a, key);
            default -> throw new NotFoundException("Unknown Lemon Squeezy action '" + actionId + "'");
        };
    }

    private String listStores(String key) {
        JsonNode r = read(get("/v1/stores", key));
        StringBuilder sb = new StringBuilder();
        for (JsonNode s : r.path("data")) {
            sb.append("• ").append(s.path("attributes").path("name").asText("(unnamed)"))
              .append(" — id ").append(s.path("id").asText("")).append('\n');
        }
        return sb.isEmpty() ? "No Lemon Squeezy stores yet." : sb.toString().strip();
    }

    private String listProducts(String key) {
        JsonNode r = read(get("/v1/products", key));
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : r.path("data")) {
            JsonNode at = p.path("attributes");
            sb.append("• ").append(at.path("name").asText("(unnamed)"))
              .append(" — ").append(at.path("price_formatted").asText("")).append('\n');
        }
        return sb.isEmpty() ? "No products yet." : sb.toString().strip();
    }

    private String recentRevenue(String key) {
        JsonNode r = read(get("/v1/orders", key));
        int count = 0;
        double total = 0;
        for (JsonNode o : r.path("data")) {
            count++;
            total += o.path("attributes").path("total").asDouble(0) / 100.0;   // cents → dollars
        }
        return count == 0 ? "No orders yet."
                : "💰 " + count + " order(s), $" + String.format(java.util.Locale.ROOT, "%.2f", total)
                  + " gross (recent). Log with revenue_log when you reconcile.";
    }

    private String createCheckout(JsonNode a, String key) {
        String storeId = a.path("storeId").asText("");
        String variantId = a.path("variantId").asText("");
        if (storeId.isBlank() || variantId.isBlank()) {
            return "Provide 'storeId' and 'variantId' (use list_stores / list_products to find them).";
        }
        String body = "{\"data\":{\"type\":\"checkouts\",\"attributes\":{},\"relationships\":{"
                + "\"store\":{\"data\":{\"type\":\"stores\",\"id\":\"" + storeId + "\"}},"
                + "\"variant\":{\"data\":{\"type\":\"variants\",\"id\":\"" + variantId + "\"}}}}}";
        JsonNode r = read(post("/v1/checkouts", body, key));
        String url = r.path("data").path("attributes").path("url").asText("");
        return url.isBlank() ? "Lemon Squeezy didn't return a checkout URL: " + r
                : "✅ Checkout link: " + url;
    }

    private String get(String path, String key) {
        return client.get().uri(path)
                .header("Authorization", "Bearer " + key)
                .header("Accept", JSON_API)
                .retrieve().body(String.class);
    }

    private String post(String path, String body, String key) {
        return client.post().uri(path)
                .header("Authorization", "Bearer " + key)
                .header("Accept", JSON_API)
                .header("Content-Type", JSON_API)
                .body(body).retrieve().body(String.class);
    }
}
