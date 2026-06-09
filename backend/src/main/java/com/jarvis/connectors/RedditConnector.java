package com.jarvis.connectors;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * Reddit (read-only, keyless) — demand-signal + research fuel for the Opportunity Scout / Growth agents:
 * what a niche is actually talking about right now. Uses Reddit's public {@code .json} endpoints (no auth),
 * with a descriptive User-Agent as Reddit requires. Posting would need OAuth (a script app) — deliberately
 * not built; this is the safe, free, read side.
 */
@Component
public class RedditConnector extends AbstractRestConnector {

    private static final String UA = "jarvis-ai-os/1.0 (personal assistant)";

    public RedditConnector(ObjectMapper mapper) {
        super(mapper);
    }

    private final RestClient client = RestClient.create("https://www.reddit.com");

    @Override public String id() { return "reddit"; }
    @Override public String name() { return "Reddit (read)"; }
    @Override public String category() { return "Research"; }
    @Override public String requiredSecret() { return null; }   // keyless public JSON

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("subreddit_hot", "Subreddit hot", "Top hot posts of a subreddit {subreddit}"),
                new ConnectorAction("search", "Search", "Search Reddit {query}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String key) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "subreddit_hot" -> hot(a.path("subreddit").asText(""));
            case "search" -> search(a.path("query").asText(""));
            default -> throw new NotFoundException("Unknown Reddit action '" + actionId + "'");
        };
    }

    private String hot(String sub) {
        if (sub.isBlank()) {
            return "Provide a 'subreddit'.";
        }
        JsonNode r = read(get("/r/" + enc(sub) + "/hot.json?limit=10"));
        return render(r, "No posts in r/" + sub + ".");
    }

    private String search(String query) {
        if (query.isBlank()) {
            return "Provide a 'query'.";
        }
        JsonNode r = read(get("/search.json?limit=10&q=" + enc(query)));
        return render(r, "No results for \"" + query + "\".");
    }

    private String render(JsonNode listing, String empty) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode child : listing.path("data").path("children")) {
            JsonNode d = child.path("data");
            sb.append("• ").append(d.path("title").asText("(untitled)"))
              .append(" — r/").append(d.path("subreddit").asText("")).append(", ")
              .append(d.path("ups").asInt(0)).append("↑ ")
              .append(d.path("num_comments").asInt(0)).append(" comments\n");
        }
        return sb.isEmpty() ? empty : sb.toString().strip();
    }

    private String get(String path) {
        return client.get().uri(path).header("User-Agent", UA).retrieve().body(String.class);
    }
}
