package com.jarvis.connectors;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * Cal.com — lets customers book time with you (service revenue) and lets Jarvis see your upcoming bookings
 * and event types. Secret in the Vault as {@code calcom-token} (a Cal.com API key). Read-only here; creating
 * bookings is typically done by the customer via your booking page.
 */
@Component
public class CalcomConnector extends AbstractRestConnector {

    public CalcomConnector(ObjectMapper mapper) {
        super(mapper);
    }

    private final RestClient client = RestClient.create("https://api.cal.com");

    @Override public String id() { return "calcom"; }
    @Override public String name() { return "Cal.com"; }
    @Override public String category() { return "Productivity"; }
    @Override public String requiredSecret() { return "calcom-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_bookings", "List bookings", "Your upcoming Cal.com bookings"),
                new ConnectorAction("list_event_types", "List event types", "Your bookable event types"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String key) throws Exception {
        Json.read(mapper, argumentsJson);   // no args needed, but keep parsing tolerant
        return switch (actionId) {
            case "list_bookings" -> listBookings(key);
            case "list_event_types" -> listEventTypes(key);
            default -> throw new NotFoundException("Unknown Cal.com action '" + actionId + "'");
        };
    }

    private String listBookings(String key) {
        JsonNode r = read(get("/v1/bookings?apiKey=" + enc(key)));
        StringBuilder sb = new StringBuilder();
        for (JsonNode b : r.path("bookings")) {
            sb.append("• ").append(b.path("title").asText("(booking)"))
              .append(" — ").append(b.path("startTime").asText("")).append('\n');
        }
        return sb.isEmpty() ? "No upcoming bookings." : sb.toString().strip();
    }

    private String listEventTypes(String key) {
        JsonNode r = read(get("/v1/event-types?apiKey=" + enc(key)));
        StringBuilder sb = new StringBuilder();
        for (JsonNode e : r.path("event_types")) {
            sb.append("• ").append(e.path("title").asText("(event)"))
              .append(" — ").append(e.path("length").asInt(0)).append(" min\n");
        }
        return sb.isEmpty() ? "No event types yet." : sb.toString().strip();
    }

    private String get(String path) {
        return client.get().uri(path).retrieve().body(String.class);
    }
}
