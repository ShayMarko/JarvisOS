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
 * Real Gumroad connector — the "sell" step of the money loop. Lists/creates products and reads sales
 * (which feeds ROI). Token from the Secrets Vault (entry {@code gumroad-token}). The agent decides when
 * to list something; creating a paid product is a real external write, so it's worth doing behind approval.
 *
 * <p>Note: Gumroad's API creates the product + price/description; attaching the downloadable file (the
 * packaged .zip) is done once in the Gumroad dashboard (their API doesn't take the file upload). So Jarvis
 * gets you 90% there — product created, priced, described — and you attach the zip + publish.
 */
@Component
@RequiredArgsConstructor
public class GumroadConnector implements Connector {

    private final RestClient client = RestClient.create("https://api.gumroad.com");
    private final ObjectMapper mapper;

    @Override public String id() { return "gumroad"; }
    @Override public String name() { return "Gumroad"; }
    @Override public String category() { return "Commerce"; }
    @Override public String requiredSecret() { return "gumroad-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return "create_product".equals(actionId) ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_products", "List products", "Your Gumroad products + prices"),
                new ConnectorAction("create_product", "Create a product",
                        "Create a product to sell {name, price (USD), description?}"),
                new ConnectorAction("list_sales", "List sales", "Recent sales (count + revenue) — feeds ROI"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "list_products" -> listProducts(token);
            case "create_product" -> createProduct(a, token);
            case "list_sales" -> listSales(token);
            default -> throw new NotFoundException("Unknown Gumroad action '" + actionId + "'");
        };
    }

    private String listProducts(String token) {
        JsonNode r = read(client.get().uri("/v2/products").header("Authorization", "Bearer " + token)
                .retrieve().body(String.class));
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : r.path("products")) {
            sb.append("• ").append(p.path("name").asText()).append(" — ")
              .append(p.path("formatted_price").asText("?"))
              .append(p.path("published").asBoolean() ? " (live)" : " (draft)").append('\n');
        }
        return sb.isEmpty() ? "No Gumroad products yet." : sb.toString().strip();
    }

    private String createProduct(JsonNode a, String token) {
        String name = a.path("name").asText("");
        if (name.isBlank()) {
            return "Provide a product 'name'.";
        }
        int cents = (int) Math.round(a.path("price").asDouble(0) * 100);
        String body = "name=" + enc(name) + "&price=" + cents
                + "&description=" + enc(a.path("description").asText(""));
        JsonNode r = read(client.post().uri("/v2/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body).retrieve().body(String.class));
        if (!r.path("success").asBoolean(false)) {
            return "Gumroad rejected the product: " + r.toString();
        }
        JsonNode p = r.path("product");
        return "✅ Created Gumroad product \"" + p.path("name").asText(name) + "\" ("
                + p.path("formatted_price").asText(cents + "c") + "). Attach the .zip + publish in the "
                + "Gumroad dashboard: " + p.path("short_url").asText("(see dashboard)");
    }

    private String listSales(String token) {
        JsonNode r = read(client.get().uri("/v2/sales").header("Authorization", "Bearer " + token)
                .retrieve().body(String.class));
        int count = 0;
        double total = 0;
        for (JsonNode s : r.path("sales")) {
            count++;
            total += s.path("price").asDouble(0) / 100.0;   // price is in cents
        }
        return count == 0 ? "No sales yet." : "💰 " + count + " sale(s), $" + String.format(java.util.Locale.ROOT,
                "%.2f", total) + " gross. Log it with revenue_log when you reconcile.";
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
