package com.jarvis.connectors;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;


/**
 * Real Printful connector — the print-on-demand lane. Jarvis generates the artwork (gpt-image-1), hosts it at
 * a public URL, then creates a Printful sync product (mug/shirt/poster) that prints + ships on demand. Token
 * from the Secrets Vault (entry {@code printful-token}); optional {@code storeId} arg → X-PF-Store-Id header.
 *
 * <p>The art must be reachable at a public {@code imageUrl} (Printful pulls it) — deploy it via the Netlify
 * or Cloudflare connector first, then pass that URL here.
 */
@Component
public class PrintfulConnector extends AbstractRestConnector {

    public PrintfulConnector(ObjectMapper mapper) {
        super(mapper);
    }

    private final RestClient client = RestClient.create("https://api.printful.com");

    @Override public String id() { return "printful"; }
    @Override public String name() { return "Printful (Print-on-Demand)"; }
    @Override public String category() { return "Commerce"; }
    @Override public String requiredSecret() { return "printful-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return "create_product".equals(actionId) ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("catalog", "Browse blanks", "Printful catalog product types (mugs/shirts/…)"),
                new ConnectorAction("variants", "Product variants", "Variant ids for a catalog product {productId}"),
                new ConnectorAction("store_products", "My products", "Products already in your Printful store {storeId?}"),
                new ConnectorAction("create_product", "Create POD product",
                        "Create a print-on-demand product {name, variantId, imageUrl, retailPrice, storeId?}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "catalog" -> catalog(token);
            case "variants" -> variants(a, token);
            case "store_products" -> storeProducts(a, token);
            case "create_product" -> createProduct(a, token);
            default -> throw new NotFoundException("Unknown Printful action '" + actionId + "'");
        };
    }

    private String catalog(String token) {
        JsonNode r = read(client.get().uri("/products").header("Authorization", "Bearer " + token)
                .retrieve().body(String.class)).path("result");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (JsonNode p : r) {
            if (n++ >= 25) {
                break;
            }
            sb.append("• id=").append(p.path("id").asInt()).append(" — ").append(p.path("title").asText()).append('\n');
        }
        return sb.isEmpty() ? "No catalog returned." : sb.toString().strip() + "\n(use 'variants' with a productId for variant ids)";
    }

    private String variants(JsonNode a, String token) {
        int productId = a.path("productId").asInt(0);
        if (productId <= 0) {
            return "Provide a catalog 'productId' (from 'catalog').";
        }
        JsonNode r = read(client.get().uri("/products/{id}", productId).header("Authorization", "Bearer " + token)
                .retrieve().body(String.class)).path("result").path("variants");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (JsonNode v : r) {
            if (n++ >= 30) {
                break;
            }
            sb.append("• variantId=").append(v.path("id").asInt()).append(" — ").append(v.path("name").asText()).append('\n');
        }
        return sb.isEmpty() ? "No variants." : sb.toString().strip();
    }

    private String storeProducts(JsonNode a, String token) {
        var spec = client.get().uri("/store/products").header("Authorization", "Bearer " + token);
        String storeId = a.path("storeId").asText("");
        if (!storeId.isBlank()) {
            spec = spec.header("X-PF-Store-Id", storeId);
        }
        JsonNode r = read(spec.retrieve().body(String.class)).path("result");
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : r) {
            sb.append("• ").append(p.path("name").asText()).append(" (").append(p.path("variants").asInt())
              .append(" variant(s))\n");
        }
        return sb.isEmpty() ? "No store products yet." : sb.toString().strip();
    }

    private String createProduct(JsonNode a, String token) throws Exception {
        String name = a.path("name").asText("");
        int variantId = a.path("variantId").asInt(0);
        String imageUrl = a.path("imageUrl").asText("");
        if (name.isBlank() || variantId <= 0 || imageUrl.isBlank()) {
            return "Provide 'name', 'variantId' (from 'variants') and a public 'imageUrl' (deploy the art first).";
        }
        String price = a.path("retailPrice").isMissingNode() ? "19.99"
                : String.format(java.util.Locale.ROOT, "%.2f", a.path("retailPrice").asDouble(19.99));

        ObjectNode body = mapper.createObjectNode();
        body.set("sync_product", mapper.createObjectNode().put("name", name));
        ArrayNode variantsArr = body.putArray("sync_variants");
        ObjectNode v = variantsArr.addObject();
        v.put("variant_id", variantId);
        v.put("retail_price", price);
        ArrayNode files = v.putArray("files");
        files.addObject().put("url", imageUrl);

        var spec = client.post().uri("/store/products").header("Authorization", "Bearer " + token);
        String storeId = a.path("storeId").asText("");
        if (!storeId.isBlank()) {
            spec = spec.header("X-PF-Store-Id", storeId);
        }
        JsonNode r = read(spec.contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body))
                .retrieve().body(String.class));
        if (r.path("code").asInt(200) >= 300) {
            return "Printful rejected the product: " + r.path("error");
        }
        return "✅ Created Printful product \"" + name + "\" ($" + price + "). Publish it to your connected store "
                + "(Etsy/Shopify) from the Printful dashboard.";
    }

}
