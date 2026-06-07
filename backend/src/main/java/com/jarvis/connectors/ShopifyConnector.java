package com.jarvis.connectors;

import java.util.List;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Real Shopify connector — your own storefront for the product lanes. Admin API access token from the
 * Secrets Vault (entry {@code shopify-token}); the store is per-call via the {@code shop} arg (the
 * myshopify subdomain, e.g. "my-store" or "my-store.myshopify.com"). Reads products/orders and creates a
 * DRAFT product (you review + publish in Shopify). create_product is approval-gated (a real external write).
 */
@Component
@RequiredArgsConstructor
public class ShopifyConnector implements Connector {

    private static final String API_VERSION = "2024-10";

    private final RestClient client = RestClient.create();
    private final ObjectMapper mapper;

    @Override public String id() { return "shopify"; }
    @Override public String name() { return "Shopify"; }
    @Override public String category() { return "Commerce"; }
    @Override public String requiredSecret() { return "shopify-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return "create_product".equals(actionId)
                ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_products", "List products", "Store products {shop}"),
                new ConnectorAction("recent_orders", "Recent orders", "Recent orders + gross revenue {shop}"),
                new ConnectorAction("create_product", "Create product",
                        "Create a DRAFT product {shop, title, description?, price, imageUrl?} — review + publish in Shopify"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        String base = baseFor(a);
        return switch (actionId) {
            case "list_products" -> listProducts(base, token);
            case "recent_orders" -> recentOrders(base, token);
            case "create_product" -> createProduct(a, base, token);
            default -> throw new NotFoundException("Unknown Shopify action '" + actionId + "'");
        };
    }

    private String listProducts(String base, String token) {
        JsonNode r = read(get(base + "/products.json?limit=50", token)).path("products");
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : r) {
            sb.append("• ").append(p.path("title").asText()).append(" (").append(p.path("status").asText()).append(")\n");
        }
        return sb.isEmpty() ? "No Shopify products yet." : sb.toString().strip();
    }

    private String recentOrders(String base, String token) {
        JsonNode r = read(get(base + "/orders.json?status=any&limit=50", token)).path("orders");
        int count = 0;
        double total = 0;
        for (JsonNode o : r) {
            count++;
            total += o.path("total_price").asDouble(0);
        }
        return count == 0 ? "No orders yet."
                : "💰 " + count + " order(s), $" + String.format(Locale.ROOT, "%.2f", total) + " gross (recent).";
    }

    private String createProduct(JsonNode a, String base, String token) throws Exception {
        String title = a.path("title").asText("");
        if (title.isBlank()) {
            return "Provide a product 'title'.";
        }
        String price = a.path("price").isMissingNode() ? "9.99"
                : String.format(Locale.ROOT, "%.2f", a.path("price").asDouble(9.99));
        ObjectNode product = mapper.createObjectNode();
        product.put("title", title);
        product.put("body_html", a.path("description").asText(""));
        product.put("status", "draft");   // safe default — owner publishes
        ArrayNode variants = product.putArray("variants");
        variants.addObject().put("price", price);
        String imageUrl = a.path("imageUrl").asText("");
        if (!imageUrl.isBlank()) {
            product.putArray("images").addObject().put("src", imageUrl);
        }
        ObjectNode body = mapper.createObjectNode();
        body.set("product", product);

        JsonNode r = read(client.post().uri(base + "/products.json")
                .header("X-Shopify-Access-Token", token).contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(body)).retrieve().body(String.class));
        JsonNode p = r.path("product");
        if (p.path("id").isMissingNode()) {
            return "Shopify rejected the product: " + r;
        }
        return "✅ Created DRAFT Shopify product \"" + title + "\" ($" + price + "). Review + publish in Shopify admin.";
    }

    private String baseFor(JsonNode a) {
        String shop = a.path("shop").asText("");
        if (shop.isBlank()) {
            throw new NotFoundException("Provide 'shop' (your myshopify subdomain, e.g. \"my-store\").");
        }
        String host = shop.contains(".") ? shop : shop + ".myshopify.com";
        return "https://" + host + "/admin/api/" + API_VERSION;
    }

    private String get(String url, String token) {
        return client.get().uri(url).header("X-Shopify-Access-Token", token).retrieve().body(String.class);
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
