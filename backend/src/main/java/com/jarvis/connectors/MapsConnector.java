package com.jarvis.connectors;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.jarvis.common.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Keyless maps / geocoding via OpenStreetMap Nominatim (spec §9 Home/IoT · Maps).
 * No credential needed. Real HTTP; Nominatim requires a descriptive User-Agent,
 * which we send. Forward geocode (place → coords) and reverse (coords → address).
 */
@Component
@RequiredArgsConstructor
public class MapsConnector implements Connector {

    private static final String UA = "JarvisAIOS/1.0 (personal local assistant)";
    private final RestClient client = RestClient.builder()
            .baseUrl("https://nominatim.openstreetmap.org")
            .defaultHeader("User-Agent", UA)
            .build();
    private final ObjectMapper mapper;

    @Override public String id() { return "maps"; }
    @Override public String name() { return "Maps / Location"; }
    @Override public String category() { return "Home / IoT"; }
    @Override public String requiredSecret() { return null; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("geocode", "Geocode", "Find coordinates for a place (args: query)"),
                new ConnectorAction("reverse", "Reverse", "Find the address for coordinates (args: lat, lon)"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String credential) throws Exception {
        JsonNode args = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "geocode" -> geocode(args.path("query").asText(""));
            case "reverse" -> reverse(args.path("lat").asText(""), args.path("lon").asText(""));
            default -> throw new NotFoundException("Unknown Maps action '" + actionId + "'");
        };
    }

    private String geocode(String query) throws Exception {
        if (query.isBlank()) {
            return "Provide a 'query' (a place or address).";
        }
        JsonNode arr = mapper.readTree(client.get()
                .uri(b -> b.path("/search").queryParam("q", query).queryParam("format", "json").queryParam("limit", 1).build())
                .retrieve().body(String.class));
        if (!arr.isArray() || arr.isEmpty()) {
            return "No location found for \"" + query + "\".";
        }
        JsonNode top = arr.get(0);
        return top.path("display_name").asText() + "\nlat " + top.path("lat").asText() + ", lon " + top.path("lon").asText();
    }

    private String reverse(String lat, String lon) throws Exception {
        if (lat.isBlank() || lon.isBlank()) {
            return "Provide 'lat' and 'lon'.";
        }
        JsonNode obj = mapper.readTree(client.get()
                .uri(b -> b.path("/reverse").queryParam("lat", lat).queryParam("lon", lon).queryParam("format", "json").build())
                .retrieve().body(String.class));
        String name = obj.path("display_name").asText("");
        return name.isBlank() ? "No address found for those coordinates." : name;
    }
}
