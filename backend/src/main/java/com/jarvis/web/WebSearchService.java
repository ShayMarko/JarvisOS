package com.jarvis.web;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Keyless web search via DuckDuckGo's Instant Answer API (spec §8 Browser /
 * Google Search). No API key required; returns the instant answer / abstract /
 * related topics. (Not a full SERP — that needs a paid provider or the browser
 * automation sidecar.)
 *
 * <p>DuckDuckGo's edge blocks requests with no browser User-Agent (returns a 403
 * HTML page), so we send one. Any failure returns a short, human-readable message
 * — never a status code or raw HTML body.
 */
@Service
@RequiredArgsConstructor
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36 JarvisAIOS/1.0";

    private final RestClient client = RestClient.builder()
            .baseUrl("https://api.duckduckgo.com")
            .defaultHeader("User-Agent", USER_AGENT)
            .defaultHeader("Accept", "application/json")
            .build();
    private final ObjectMapper mapper;


    public String search(String query) {
        if (query == null || query.isBlank()) {
            return "Please give me something to search for.";
        }
        try {
            String raw = client.get()
                    .uri(b -> b.queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("no_html", "1")
                            .queryParam("skip_disambig", "1")
                            .build())
                    .retrieve().body(String.class);
            return parse(raw, query);
        } catch (Exception e) {
            // Keep the technical detail in the logs; give the user a clean line.
            log.debug("Web search failed for \"{}\": {}", query, e.getMessage());
            return "I couldn't reach web search just now — the provider declined the request. "
                    + "Please try again in a moment.";
        }
    }

    /** Pure parse of a DuckDuckGo IA JSON response — unit-tested without network. */
    String parse(String raw, String query) {
        try {
            JsonNode root = mapper.readTree(raw);
            String answer = firstNonBlank(
                    root.path("Answer").asText(""),
                    root.path("AbstractText").asText(""),
                    root.path("Definition").asText(""));
            StringBuilder sb = new StringBuilder();
            if (!answer.isBlank()) {
                sb.append(answer);
                String url = firstNonBlank(root.path("AbstractURL").asText(""), root.path("DefinitionURL").asText(""));
                if (!url.isBlank()) {
                    sb.append("\nSource: ").append(url);
                }
            }
            JsonNode related = root.path("RelatedTopics");
            int shown = 0;
            for (JsonNode t : related) {
                String text = t.path("Text").asText("");
                if (!text.isBlank() && shown < 4) {
                    sb.append(sb.isEmpty() && shown == 0 ? "" : "\n• ").append(text);
                    shown++;
                }
            }
            return sb.length() == 0
                    ? "No instant answer for \"" + query + "\". (DuckDuckGo's instant-answer API only covers well-known topics; full web results need the browser automation step.)"
                    : sb.toString().trim();
        } catch (Exception e) {
            log.debug("Could not parse web search response: {}", e.getMessage());
            return "I couldn't read the web search response. Please try again.";
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
