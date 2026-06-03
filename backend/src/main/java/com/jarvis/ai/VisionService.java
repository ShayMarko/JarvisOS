package com.jarvis.ai;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Lets Jarvis SEE — sends an image to a vision-capable cloud model (OpenAI gpt-4o / Anthropic Claude)
 * and returns a plain-text description/answer. Kept as a separate service (not the text agent loop) so
 * the loop stays text-only; the agent calls a vision TOOL, gets text back, and reasons over it.
 *
 * <p>Provider is chosen by which key is set (Anthropic preferred, then OpenAI). The local model can't
 * see, so with no key it returns a clear message — i.e. it's wired and ready, dormant until a key.
 * Request bodies are built by pure methods (testable without the network).
 */
@Service
@RequiredArgsConstructor
public class VisionService {

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    private final JarvisAiProperties ai;
    private final ObjectMapper mapper;

    /** Describe/answer about an image. Returns text, or a clear message if no vision provider is set. */
    public String describe(byte[] image, String mimeType, String question) {
        if (image == null || image.length == 0) {
            return "Error: no image data.";
        }
        String q = question == null || question.isBlank() ? "Describe what's in this image in detail." : question;
        String b64 = Base64.getEncoder().encodeToString(image);
        String mime = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType;
        try {
            if (notBlank(ai.getAnthropicApiKey())) {
                return anthropic(b64, mime, q);
            }
            if (notBlank(ai.getOpenaiApiKey())) {
                return openai(b64, mime, q);
            }
            return "Vision needs an OpenAI or Anthropic API key — the local model can't see images. "
                    + "Add one in Settings, then try again.";
        } catch (RuntimeException e) {
            log.warn("Vision call failed: {}", e.getMessage());
            return "Couldn't analyze the image: " + e.getMessage();
        }
    }

    // --- Anthropic ---------------------------------------------------------
    Map<String, Object> anthropicBody(String b64, String mime, String question) {
        return Map.of(
                "model", ai.getModel(),
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", List.of(
                        Map.of("type", "image", "source",
                                Map.of("type", "base64", "media_type", mime, "data", b64)),
                        Map.of("type", "text", "text", question)))));
    }

    private String anthropic(String b64, String mime, String question) {
        RestClient http = client("https://api.anthropic.com")
                .mutate().defaultHeader("x-api-key", ai.getAnthropicApiKey())
                .defaultHeader("anthropic-version", "2023-06-01").build();
        String raw = http.post().uri("/v1/messages").body(anthropicBody(b64, mime, question))
                .retrieve().body(String.class);
        return text(raw, "/content/0/text");
    }

    // --- OpenAI ------------------------------------------------------------
    Map<String, Object> openaiBody(String b64, String mime, String question) {
        return Map.of(
                "model", ai.getOpenaiModel(),
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", question),
                        Map.of("type", "image_url", "image_url",
                                Map.of("url", "data:" + mime + ";base64," + b64))))));
    }

    private String openai(String b64, String mime, String question) {
        RestClient http = client(ai.getOpenaiBaseUrl())
                .mutate().defaultHeader("authorization", "Bearer " + ai.getOpenaiApiKey()).build();
        String raw = http.post().uri("/chat/completions").body(openaiBody(b64, mime, question))
                .retrieve().body(String.class);
        return text(raw, "/choices/0/message/content");
    }

    private String text(String raw, String pointer) {
        try {
            JsonNode node = mapper.readTree(raw == null ? "{}" : raw).at(pointer);
            String t = node.isMissingNode() ? "" : node.asText("");
            return t.isBlank() ? "(no description returned)" : t;
        } catch (Exception e) {
            return "Couldn't parse the vision response.";
        }
    }

    private static RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(rf).baseUrl(baseUrl)
                .defaultHeader("content-type", "application/json").build();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
