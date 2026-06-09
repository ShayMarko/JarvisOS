package com.jarvis.connectors;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.security.RiskLevel;

/**
 * Twilio — SMS + WhatsApp, so Jarvis can reach you (and customers) on a phone, and you can drive Jarvis
 * phone-first. Secret in the Vault as {@code twilio-token} = {@code "<AccountSID>:<AuthToken>"} (Basic auth).
 * Sending is approval-gated (it costs money + reaches real people).
 */
@Component
public class TwilioConnector extends AbstractRestConnector {

    public TwilioConnector(ObjectMapper mapper) {
        super(mapper);
    }

    private final RestClient client = RestClient.create("https://api.twilio.com");

    @Override public String id() { return "twilio"; }
    @Override public String name() { return "Twilio (SMS/WhatsApp)"; }
    @Override public String category() { return "Communication"; }
    @Override public String requiredSecret() { return "twilio-token"; }

    @Override
    public RiskLevel actionRisk(String actionId) {
        return RiskLevel.HIGH;   // every action here sends a real message
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("send_sms", "Send SMS", "Send a text {to, from, body}"),
                new ConnectorAction("send_whatsapp", "Send WhatsApp", "Send a WhatsApp message {to, from, body}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String key) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "send_sms" -> send(a, key, false);
            case "send_whatsapp" -> send(a, key, true);
            default -> throw new NotFoundException("Unknown Twilio action '" + actionId + "'");
        };
    }

    private String send(JsonNode a, String key, boolean whatsapp) {
        String to = a.path("to").asText("");
        String from = a.path("from").asText("");
        String body = a.path("body").asText("");
        if (to.isBlank() || from.isBlank() || body.isBlank()) {
            return "Provide 'to', 'from' and 'body'.";
        }
        if (key == null || !key.contains(":")) {
            return "twilio-token must be stored as \"<AccountSID>:<AuthToken>\".";
        }
        String sid = key.substring(0, key.indexOf(':'));
        String prefix = whatsapp ? "whatsapp:" : "";
        String form = "To=" + enc(prefix + to) + "&From=" + enc(prefix + from) + "&Body=" + enc(body);
        String auth = Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        JsonNode r = read(client.post().uri("/2010-04-01/Accounts/" + enc(sid) + "/Messages.json")
                .header("Authorization", "Basic " + auth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(String.class));
        String s = r.path("sid").asText("");
        return s.isBlank() ? "Twilio error: " + r : "✅ Sent (" + (whatsapp ? "WhatsApp" : "SMS") + "), id " + s;
    }
}
