package com.jarvis.connectors;

import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.oauth.OAuthService;
import com.jarvis.security.RiskLevel;

/**
 * Real Gmail connector — a working mail client for the Email Agent: read/search the inbox, read a full
 * message, draft a reply, and send. Uses the shared Google OAuth connection via {@link OAuthService}
 * (auto-refreshing access token — no more 1-hour expiry), so its vault/connected key is {@code oauth:google},
 * the same one Google Drive uses. Authorize once via {@code /oauth} (the Google provider's scopes must include
 * gmail.readonly + gmail.send, or gmail.modify).
 *
 * <p>{@code send} is HIGH risk (real outbound mail) → routes through the Approval Center. {@code create_draft}
 * is safe (saves a draft you review in Gmail), so it stays autonomous.
 */
@Component
@RequiredArgsConstructor
public class GmailConnector implements Connector {

    private static final int MAX_BODY = 3000;

    private final RestClient client = RestClient.create("https://gmail.googleapis.com/gmail/v1");
    private final ObjectMapper mapper;
    private final OAuthService oauth;

    @Override public String id() { return "gmail"; }
    @Override public String name() { return "Gmail"; }
    @Override public String category() { return "Productivity"; }
    @Override public String requiredSecret() { return "oauth:google"; }

    @Override
    public RiskLevel actionRisk(String actionId) {
        return "send".equals(actionId) ? RiskLevel.HIGH : RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_recent", "List recent", "Recent emails (subject + from) {max?}"),
                new ConnectorAction("search", "Search mail", "Search the inbox {query, max?} (Gmail search syntax)"),
                new ConnectorAction("get_message", "Read a message", "Full subject/from/date + body {id}"),
                new ConnectorAction("create_draft", "Draft a reply", "Save a draft (you review/send) {to, subject, body}"),
                new ConnectorAction("send", "Send email", "Send an email NOW {to, subject, body} — approval-gated"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String credential) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        String token = oauth.accessToken("google");   // fresh, auto-refreshed
        return switch (actionId) {
            case "list_recent" -> listMessages("", a.path("max").asInt(5), token);
            case "search" -> search(a, token);
            case "get_message" -> getMessage(a, token);
            case "create_draft" -> createDraft(a, token);
            case "send" -> send(a, token);
            default -> throw new NotFoundException("Unknown Gmail action '" + actionId + "'");
        };
    }

    private String search(JsonNode a, String token) throws Exception {
        String query = a.path("query").asText("");
        if (query.isBlank()) {
            return "Provide a 'query' (Gmail search syntax, e.g. 'from:stripe newer_than:7d').";
        }
        return listMessages(query, a.path("max").asInt(10), token);
    }

    private String listMessages(String query, int max, String token) throws Exception {
        int n = Math.min(Math.max(max, 1), 25);
        String uri = "/users/me/messages?maxResults=" + n
                + (query.isBlank() ? "" : "&q=" + enc(query));
        JsonNode list = mapper.readTree(get(uri, token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode m : list.path("messages")) {
            String id = m.path("id").asText();
            JsonNode msg = mapper.readTree(get("/users/me/messages/" + id
                    + "?format=metadata&metadataHeaders=Subject&metadataHeaders=From", token));
            sb.append("• [").append(id).append("] ").append(header(msg, "Subject"))
              .append("  —  ").append(header(msg, "From")).append('\n');
        }
        return sb.isEmpty() ? "No matching emails." : sb.toString().strip()
                + "\n(use get_message with an [id] to read the full body)";
    }

    private String getMessage(JsonNode a, String token) throws Exception {
        String id = a.path("id").asText("");
        if (id.isBlank()) {
            return "Provide a message 'id' (from list_recent/search).";
        }
        JsonNode msg = mapper.readTree(get("/users/me/messages/" + id + "?format=full", token));
        JsonNode payload = msg.path("payload");
        String body = bodyText(payload);
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY) + "\n…(truncated)";
        }
        return "Subject: " + header(msg, "Subject") + "\nFrom: " + header(msg, "From")
                + "\nDate: " + header(msg, "Date") + "\n\n" + body.strip();
    }

    private String createDraft(JsonNode a, String token) throws Exception {
        String raw = buildRaw(a);
        if (raw == null) {
            return "Provide 'to', 'subject' and 'body'.";
        }
        String payload = "{\"message\":{\"raw\":\"" + raw + "\"}}";
        JsonNode r = mapper.readTree(post("/users/me/drafts", payload, token));
        String draftId = r.path("id").asText("");
        return draftId.isBlank() ? "Gmail did not create the draft: " + r
                : "📝 Draft saved (id=" + draftId + ") — review and send it in Gmail.";
    }

    private String send(JsonNode a, String token) throws Exception {
        String raw = buildRaw(a);
        if (raw == null) {
            return "Provide 'to', 'subject' and 'body'.";
        }
        String payload = "{\"raw\":\"" + raw + "\"}";
        JsonNode r = mapper.readTree(post("/users/me/messages/send", payload, token));
        String sentId = r.path("id").asText("");
        return sentId.isBlank() ? "Gmail did not send the message: " + r
                : "✅ Email sent to " + a.path("to").asText() + " (id=" + sentId + ").";
    }

    /** Build a base64url-encoded RFC-822 message, or null if required fields are missing. */
    private String buildRaw(JsonNode a) {
        String to = a.path("to").asText("");
        String subject = a.path("subject").asText("");
        String body = a.path("body").asText("");
        if (to.isBlank() || subject.isBlank() || body.isBlank()) {
            return null;
        }
        String mime = "To: " + to + "\r\nSubject: " + subject
                + "\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + body;
        return Base64.getUrlEncoder().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
    }

    /** Extract a readable body from a Gmail payload tree: prefer text/plain, fall back to stripped text/html. */
    private String bodyText(JsonNode payload) {
        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(payload);
        String htmlData = null;
        while (!stack.isEmpty()) {
            JsonNode p = stack.pop();
            String mime = p.path("mimeType").asText("");
            String data = p.path("body").path("data").asText("");
            if (!data.isEmpty()) {
                if (mime.startsWith("text/plain")) {
                    return decode(data);
                }
                if (mime.startsWith("text/html") && htmlData == null) {
                    htmlData = data;
                }
            }
            for (JsonNode c : p.path("parts")) {
                stack.push(c);
            }
        }
        return htmlData != null ? decode(htmlData).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").strip()
                : "(no readable text body)";
    }

    private static String decode(String urlSafeBase64) {
        try {
            return new String(Base64.getUrlDecoder().decode(urlSafeBase64), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return "(unreadable body)";
        }
    }

    private String header(JsonNode message, String name) {
        for (JsonNode h : message.path("payload").path("headers")) {
            if (h.path("name").asText().equalsIgnoreCase(name)) {
                return h.path("value").asText("");
            }
        }
        return "(no " + name + ")";
    }

    private String get(String uri, String token) {
        return client.get().uri(uri).header("Authorization", "Bearer " + token).retrieve().body(String.class);
    }

    private String post(String uri, String jsonBody, String token) {
        return client.post().uri(uri).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(jsonBody).retrieve().body(String.class);
    }

    private static String enc(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
