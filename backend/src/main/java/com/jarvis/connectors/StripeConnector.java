package com.jarvis.connectors;

import java.util.List;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;


/**
 * Real Stripe connector — payment truth + the ability for the Micro-API lane to bill its OWN customers
 * instead of relying on RapidAPI's billing. Secret key from the Secrets Vault (entry {@code stripe-token}).
 * Reads products/revenue/balance, and creates a shareable payment link (product → price → link) in one call.
 */
@Component
public class StripeConnector extends AbstractRestConnector {

    public StripeConnector(ObjectMapper mapper) {
        super(mapper);
    }

    private final RestClient client = RestClient.create("https://api.stripe.com");

    @Override public String id() { return "stripe"; }
    @Override public String name() { return "Stripe"; }
    @Override public String category() { return "Commerce"; }
    @Override public String requiredSecret() { return "stripe-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return "create_payment_link".equals(actionId) || "create_subscription_link".equals(actionId)
                ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_products", "List products", "Your Stripe products"),
                new ConnectorAction("recent_revenue", "Recent revenue", "Sum of recent successful charges (feeds ROI)"),
                new ConnectorAction("balance", "Balance", "Available + pending Stripe balance"),
                new ConnectorAction("create_payment_link", "Create payment link",
                        "Create a sellable payment link {name, price (USD), currency?} — product+price+link in one step"),
                new ConnectorAction("create_subscription_link", "Create subscription link",
                        "Create a RECURRING payment link {name, price (USD), interval? (month|year), currency?} — "
                        + "the recurring-revenue rail for a micro-SaaS or membership"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String key) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "list_products" -> listProducts(key);
            case "recent_revenue" -> recentRevenue(key);
            case "balance" -> balance(key);
            case "create_payment_link" -> createPaymentLink(a, key);
            case "create_subscription_link" -> createSubscriptionLink(a, key);
            default -> throw new NotFoundException("Unknown Stripe action '" + actionId + "'");
        };
    }

    private String listProducts(String key) {
        JsonNode r = read(get("/v1/products?limit=20", key));
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : r.path("data")) {
            sb.append("• ").append(p.path("name").asText("(unnamed)"))
              .append(p.path("active").asBoolean() ? "" : " (inactive)").append('\n');
        }
        return sb.isEmpty() ? "No Stripe products yet." : sb.toString().strip();
    }

    private String recentRevenue(String key) {
        JsonNode r = read(get("/v1/charges?limit=100", key));
        int count = 0;
        double total = 0;
        for (JsonNode c : r.path("data")) {
            if (c.path("paid").asBoolean() && !c.path("refunded").asBoolean()) {
                count++;
                total += c.path("amount").asDouble(0) / 100.0;
            }
        }
        return count == 0 ? "No successful charges yet."
                : "💰 " + count + " paid charge(s), $" + fmt(total) + " gross (last 100). Log with revenue_log when you reconcile.";
    }

    private String balance(String key) {
        JsonNode r = read(get("/v1/balance", key));
        double avail = sumAmounts(r.path("available"));
        double pending = sumAmounts(r.path("pending"));
        return "Stripe balance — available $" + fmt(avail) + ", pending $" + fmt(pending) + ".";
    }

    private String createPaymentLink(JsonNode a, String key) {
        String name = a.path("name").asText("");
        if (name.isBlank()) {
            return "Provide a product 'name'.";
        }
        int cents = (int) Math.round(a.path("price").asDouble(0) * 100);
        if (cents <= 0) {
            return "Provide a positive 'price' in USD.";
        }
        String currency = a.path("currency").asText("usd").toLowerCase();
        JsonNode product = read(post("/v1/products", "name=" + enc(name), key));
        String productId = product.path("id").asText("");
        JsonNode price = read(post("/v1/prices",
                "product=" + enc(productId) + "&unit_amount=" + cents + "&currency=" + enc(currency), key));
        String priceId = price.path("id").asText("");
        JsonNode link = read(post("/v1/payment_links",
                "line_items[0][price]=" + enc(priceId) + "&line_items[0][quantity]=1", key));
        String url = link.path("url").asText("");
        return url.isBlank() ? "Stripe didn't return a link: " + link
                : "✅ Payment link for \"" + name + "\" ($" + fmt(cents / 100.0) + "): " + url;
    }

    private String createSubscriptionLink(JsonNode a, String key) {
        String name = a.path("name").asText("");
        if (name.isBlank()) {
            return "Provide a product 'name'.";
        }
        int cents = (int) Math.round(a.path("price").asDouble(0) * 100);
        if (cents <= 0) {
            return "Provide a positive 'price' in USD.";
        }
        String currency = a.path("currency").asText("usd").toLowerCase();
        String interval = a.path("interval").asText("month").toLowerCase();
        if (!interval.equals("month") && !interval.equals("year")) {
            interval = "month";
        }
        JsonNode product = read(post("/v1/products", "name=" + enc(name), key));
        String productId = product.path("id").asText("");
        // A RECURRING price (this is what makes it a subscription rather than a one-off).
        JsonNode price = read(post("/v1/prices",
                "product=" + enc(productId) + "&unit_amount=" + cents + "&currency=" + enc(currency)
                        + "&recurring[interval]=" + enc(interval), key));
        String priceId = price.path("id").asText("");
        JsonNode link = read(post("/v1/payment_links",
                "line_items[0][price]=" + enc(priceId) + "&line_items[0][quantity]=1", key));
        String url = link.path("url").asText("");
        return url.isBlank() ? "Stripe didn't return a link: " + link
                : "✅ Subscription link for \"" + name + "\" ($" + fmt(cents / 100.0) + "/" + interval + "): " + url;
    }

    private double sumAmounts(JsonNode arr) {
        double t = 0;
        for (JsonNode n : arr) {
            t += n.path("amount").asDouble(0) / 100.0;
        }
        return t;
    }

    private String get(String path, String key) {
        return client.get().uri(path).header("Authorization", "Bearer " + key).retrieve().body(String.class);
    }

    private String post(String path, String form, String key) {
        return client.post().uri(path).header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class);
    }



    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
