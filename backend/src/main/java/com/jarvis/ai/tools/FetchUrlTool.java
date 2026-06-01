package com.jarvis.ai.tools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.config.JarvisLimitsProperties;
import com.jarvis.security.RestrictionPolicy;

import lombok.RequiredArgsConstructor;

/** Fetches a web page and returns its visible text (static HTML, no JS). */
@Component
@RequiredArgsConstructor
public class FetchUrlTool implements Tool {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    private final ObjectMapper mapper;
    private final JarvisLimitsProperties limits;
    private final RestrictionPolicy policy;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("fetch_url", "Fetch a web page and return its visible text (static HTML, no JS).",
                "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}");
    }

    @Override
    public String execute(String args) {
        String url = ToolArgs.str(mapper, args, "url");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Only http(s) URLs are supported.";
        }
        var denied = policy.denyReasonForHost(url);
        if (denied.isPresent()) {
            return "Blocked: " + denied.get();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(limits.getWebFetchTimeoutSeconds()))
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
            int max = limits.getWebFetchMaxChars();
            return text.length() > max ? text.substring(0, max) + "…(truncated)" : text;
        } catch (Exception e) {
            return "Could not fetch " + url + ": " + e.getMessage();
        }
    }
}
