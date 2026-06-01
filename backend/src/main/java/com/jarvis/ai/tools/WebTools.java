package com.jarvis.ai.tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.web.WebSearchService;

/** Web tools (spec §8): keyless search + a lightweight real URL fetch. */
public final class WebTools {

    private WebTools() {}

    private static String arg(ObjectMapper m, String json, String key) {
        try {
            return m.readTree(json == null || json.isBlank() ? "{}" : json).path(key).asText("");
        } catch (Exception e) {
            return "";
        }
    }

    @Component
    public static class WebSearchTool implements Tool {
        private final WebSearchService web;
        private final ObjectMapper mapper;

        public WebSearchTool(WebSearchService web, ObjectMapper mapper) {
            this.web = web;
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("web_search", "Search the web for an instant answer (DuckDuckGo, no key).",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
        }

        @Override
        public String execute(String args) {
            try {
                return web.search(arg(mapper, args, "query"));
            } catch (Exception e) {
                return "Web search error: " + e.getMessage();
            }
        }
    }

    @Component
    public static class FetchUrlTool implements Tool {
        private static final HttpClient HTTP = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8)).build();
        private final ObjectMapper mapper;

        public FetchUrlTool(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec("fetch_url", "Fetch a web page and return its visible text (static HTML, no JS).",
                    "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}");
        }

        @Override
        public String execute(String args) {
            String url = arg(mapper, args, "url");
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return "Only http(s) URLs are supported.";
            }
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(12))
                        .header("User-Agent", "JarvisAIOS/1.0")
                        .GET().build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                String text = resp.body()
                        .replaceAll("(?s)<script.*?</script>", " ")
                        .replaceAll("(?s)<style.*?</style>", " ")
                        .replaceAll("<[^>]+>", " ")
                        .replaceAll("&[a-zA-Z#0-9]+;", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                return text.length() > 2000 ? text.substring(0, 2000) + "…(truncated)" : text;
            } catch (Exception e) {
                return "Could not fetch " + url + ": " + e.getMessage();
            }
        }
    }
}
