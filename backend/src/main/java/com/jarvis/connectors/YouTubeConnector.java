package com.jarvis.connectors;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * YouTube Data API (read) — research + analytics for the Faceless Video lane: find videos in a niche and
 * read a channel's public stats. Secret in the Vault as {@code youtube-token} (a Google API key). UPLOADING
 * is handled by the Ayrshare connector ({@code post_video}, which manages YouTube's OAuth) — this connector
 * is the free, key-only read side, so the two complement each other without a second OAuth flow.
 */
@Component
public class YouTubeConnector extends AbstractRestConnector {

    public YouTubeConnector(ObjectMapper mapper) {
        super(mapper);
    }

    private final RestClient client = RestClient.create("https://www.googleapis.com/youtube/v3");

    @Override public String id() { return "youtube"; }
    @Override public String name() { return "YouTube (read)"; }
    @Override public String category() { return "Media"; }
    @Override public String requiredSecret() { return "youtube-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("search", "Search videos", "Find videos in a niche {query}"),
                new ConnectorAction("channel_stats", "Channel stats", "Public stats for a channel {channelId}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String key) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "search" -> search(a.path("query").asText(""), key);
            case "channel_stats" -> channelStats(a.path("channelId").asText(""), key);
            default -> throw new NotFoundException("Unknown YouTube action '" + actionId + "'");
        };
    }

    private String search(String query, String key) {
        if (query.isBlank()) {
            return "Provide a 'query'.";
        }
        JsonNode r = read(get("/search?part=snippet&type=video&maxResults=10&q=" + enc(query) + "&key=" + enc(key)));
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : r.path("items")) {
            JsonNode sn = item.path("snippet");
            sb.append("• ").append(sn.path("title").asText("(untitled)"))
              .append(" — ").append(sn.path("channelTitle").asText("")).append('\n');
        }
        return sb.isEmpty() ? "No videos for \"" + query + "\"." : sb.toString().strip();
    }

    private String channelStats(String channelId, String key) {
        if (channelId.isBlank()) {
            return "Provide a 'channelId'.";
        }
        JsonNode r = read(get("/channels?part=statistics,snippet&id=" + enc(channelId) + "&key=" + enc(key)));
        JsonNode item = r.path("items").path(0);
        if (item.isMissingNode()) {
            return "No channel found for id " + channelId + ".";
        }
        JsonNode st = item.path("statistics");
        return "📺 " + item.path("snippet").path("title").asText("") + " — "
                + st.path("subscriberCount").asText("?") + " subs, "
                + st.path("videoCount").asText("?") + " videos, "
                + st.path("viewCount").asText("?") + " total views.";
    }

    private String get(String path) {
        return client.get().uri(path).retrieve().body(String.class);
    }
}
