package com.jarvis.web;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Keyless web search via DuckDuckGo's Instant Answer API (spec §8 Browser /
 * Google Search). No API key required; returns the instant answer / abstract /
 * related topics. (Not a full SERP — that needs a paid provider or the browser
 * automation sidecar.)
 */
@Service
@RequiredArgsConstructor
public class WebSearchService {

    private final RestClient client = RestClient.create("https://api.duckduckgo.com");
    private final ObjectMapper mapper;


    public String search(String query) {
        if (query == null || query.isBlank()) {
            return "Empty query.";
        }
        String raw = client.get()
                .uri(b -> b.queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("no_html", "1")
                        .queryParam("skip_disambig", "1")
                        .build())
                .retrieve().body(String.class);
        return parse(raw, query);
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
            return sb.length() == 0 ? "No instant answer for \"" + query + "\"." : sb.toString().trim();
        } catch (Exception e) {
            return "Web search failed: " + e.getMessage();
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
