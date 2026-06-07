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

import lombok.RequiredArgsConstructor;

/**
 * Real Resend connector — lets Jarvis send transactional + launch/newsletter email, so the business can own
 * an EMAIL AUDIENCE (the one asset platforms can't take away). API key from the Secrets Vault (entry
 * {@code resend-token}). Sender domain must be verified in Resend; the test sender works out of the box.
 */
@Component
@RequiredArgsConstructor
public class ResendConnector implements Connector {

    private static final String DEFAULT_FROM = "Jarvis <onboarding@resend.dev>";

    private final RestClient client = RestClient.create("https://api.resend.com");
    private final ObjectMapper mapper;

    @Override public String id() { return "resend"; }
    @Override public String name() { return "Resend (Email)"; }
    @Override public String category() { return "Marketing"; }
    @Override public String requiredSecret() { return "resend-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return "send_email".equals(actionId) ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("send_email", "Send email",
                        "Send an email {to, subject, html? | text?, from?} — to may be a single address or a list"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String key) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        if (!"send_email".equals(actionId)) {
            throw new NotFoundException("Unknown Resend action '" + actionId + "'");
        }
        String subject = a.path("subject").asText("");
        String html = a.path("html").asText("");
        String text = a.path("text").asText("");
        if (subject.isBlank() || (html.isBlank() && text.isBlank())) {
            return "Provide 'subject' and at least one of 'html' or 'text'.";
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("from", a.path("from").asText(DEFAULT_FROM));
        ArrayNode to = body.putArray("to");
        JsonNode toNode = a.path("to");
        if (toNode.isArray() && toNode.size() > 0) {
            toNode.forEach(t -> to.add(t.asText()));
        } else if (toNode.isTextual() && !toNode.asText().isBlank()) {
            for (String t : toNode.asText().split("\\s*,\\s*")) {
                to.add(t);
            }
        } else {
            return "Provide 'to' (a recipient address or list).";
        }
        body.put("subject", subject);
        if (!html.isBlank()) {
            body.put("html", html);
        }
        if (!text.isBlank()) {
            body.put("text", text);
        }
        JsonNode r = read(client.post().uri("/emails").header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body))
                .retrieve().body(String.class));
        String id = r.path("id").asText("");
        return id.isBlank() ? "Resend did not accept the email: " + r
                : "✅ Email sent to " + to + " (id=" + id + ").";
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
