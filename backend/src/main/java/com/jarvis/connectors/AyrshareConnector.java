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
 * Real Ayrshare connector — ONE integration that posts to many social networks (X/Twitter, Reddit, LinkedIn,
 * Facebook, Instagram, Pinterest, …). This is the distribution rail the Growth agent needs to actually PUBLISH
 * the assets it drafts, not just file them. API key from the Secrets Vault (entry {@code ayrshare-token}).
 */
@Component
@RequiredArgsConstructor
public class AyrshareConnector implements Connector {

    private final RestClient client = RestClient.create("https://api.ayrshare.com");
    private final ObjectMapper mapper;

    @Override public String id() { return "ayrshare"; }
    @Override public String name() { return "Ayrshare (Social)"; }
    @Override public String category() { return "Marketing"; }
    @Override public String requiredSecret() { return "ayrshare-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return ("post".equals(actionId) || "post_video".equals(actionId))
                ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("post", "Post to social",
                        "Publish/schedule a post {post, platforms:[twitter,reddit,…], scheduleDate?}"),
                new ConnectorAction("post_video", "Publish a video",
                        "Upload a short-form VIDEO to YouTube/TikTok/Reels {videoUrl, title?, post?, "
                        + "platforms:[youtube,tiktok,instagram], scheduleDate?} — finishes the faceless-video lane"),
                new ConnectorAction("profiles", "Connected accounts", "Which social accounts are linked"),
                new ConnectorAction("history", "Recent posts", "Recently published posts + status"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String key) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "post" -> post(a, key);
            case "post_video" -> postVideo(a, key);
            case "profiles" -> get("/api/profiles", key);
            case "history" -> get("/api/history", key);
            default -> throw new NotFoundException("Unknown Ayrshare action '" + actionId + "'");
        };
    }

    private String postVideo(JsonNode a, String key) throws Exception {
        String videoUrl = a.path("videoUrl").asText("");
        if (videoUrl.isBlank()) {
            return "Provide a public 'videoUrl' (host the rendered MP4 first, e.g. via Cloudflare/Netlify).";
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("post", a.path("post").asText(a.path("title").asText("")));   // caption / description
        ArrayNode platforms = body.putArray("platforms");
        JsonNode given = a.path("platforms");
        if (given.isArray() && given.size() > 0) {
            given.forEach(p -> platforms.add(p.asText()));
        } else if (given.isTextual() && !given.asText().isBlank()) {
            for (String p : given.asText().split("\\s*,\\s*")) {
                platforms.add(p);
            }
        } else {
            platforms.add("youtube");
            platforms.add("tiktok");
        }
        body.putArray("mediaUrls").add(videoUrl);
        body.put("isVideo", true);
        String title = a.path("title").asText("");
        if (!title.isBlank()) {
            body.set("youTubeOptions", mapper.createObjectNode().put("title", title));
        }
        if (a.hasNonNull("scheduleDate")) {
            body.put("scheduleDate", a.path("scheduleDate").asText());
        }
        JsonNode r = read(client.post().uri("/api/post").header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body))
                .retrieve().body(String.class));
        String status = r.path("status").asText("");
        if (!"success".equalsIgnoreCase(status) && !"scheduled".equalsIgnoreCase(status)) {
            return "Ayrshare did not accept the video: " + r;
        }
        return "✅ Video published to " + platforms + " (id=" + r.path("id").asText("?") + ").";
    }

    private String post(JsonNode a, String key) throws Exception {
        String text = a.path("post").asText("");
        if (text.isBlank()) {
            return "Provide the 'post' text.";
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("post", text);
        ArrayNode platforms = body.putArray("platforms");
        JsonNode given = a.path("platforms");
        if (given.isArray() && given.size() > 0) {
            given.forEach(p -> platforms.add(p.asText()));
        } else if (given.isTextual() && !given.asText().isBlank()) {
            for (String p : given.asText().split("\\s*,\\s*")) {
                platforms.add(p);
            }
        } else {
            platforms.add("twitter");   // sensible default
        }
        if (a.hasNonNull("scheduleDate")) {
            body.put("scheduleDate", a.path("scheduleDate").asText());
        }
        JsonNode r = read(client.post().uri("/api/post").header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(body))
                .retrieve().body(String.class));
        String status = r.path("status").asText("");
        if (!"success".equalsIgnoreCase(status) && !"scheduled".equalsIgnoreCase(status)) {
            return "Ayrshare did not accept the post: " + r;
        }
        return "✅ Posted to " + platforms + " (id=" + r.path("id").asText("?") + ").";
    }

    private String get(String path, String key) {
        JsonNode r = read(client.get().uri(path).header("Authorization", "Bearer " + key)
                .retrieve().body(String.class));
        return r.toString();
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
