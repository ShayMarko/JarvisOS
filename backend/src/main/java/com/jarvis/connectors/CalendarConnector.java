package com.jarvis.connectors;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * Real Google Calendar connector — calls the Calendar REST API with a Google
 * OAuth access token from the Secrets Vault (entry {@code google-calendar-token}).
 */
@Component
public class CalendarConnector implements Connector {

    private final RestClient client = RestClient.create("https://www.googleapis.com/calendar/v3");
    private final ObjectMapper mapper;

    public CalendarConnector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String id() { return "calendar"; }
    @Override public String name() { return "Google Calendar"; }
    @Override public String category() { return "Productivity"; }
    @Override public String requiredSecret() { return "google-calendar-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(new ConnectorAction("today_events", "Today", "Today's events on your primary calendar"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        if (!"today_events".equals(actionId)) {
            throw new NotFoundException("Unknown Calendar action '" + actionId + "'");
        }
        ZoneId zone = ZoneId.systemDefault();
        String timeMin = LocalDate.now(zone).atStartOfDay(zone).toInstant().toString();
        String timeMax = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toString();

        String raw = client.get()
                .uri(b -> b.path("/calendars/primary/events")
                        .queryParam("timeMin", timeMin)
                        .queryParam("timeMax", timeMax)
                        .queryParam("singleEvents", "true")
                        .queryParam("orderBy", "startTime")
                        .queryParam("maxResults", "15")
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve().body(String.class);

        JsonNode items = mapper.readTree(raw).path("items");
        StringBuilder sb = new StringBuilder();
        for (JsonNode e : items) {
            String start = e.path("start").path("dateTime").asText(e.path("start").path("date").asText("?"));
            sb.append(start).append("  ").append(e.path("summary").asText("(no title)")).append("\n");
        }
        return sb.isEmpty() ? "No events today." : sb.toString().trim();
    }
}
