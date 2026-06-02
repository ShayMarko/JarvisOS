package com.jarvis.kb;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Neural embeddings via a local Ollama server ({@code /api/embeddings}). Real
 * semantic vectors (e.g. {@code nomic-embed-text}, 768-dim) for KB retrieval —
 * far stronger than the offline hashing model — with no API key. Used when Ollama
 * is reachable at startup; otherwise the KB falls back to {@link HashingEmbeddingModel}.
 */
public class OllamaEmbeddingModel implements EmbeddingModel {

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;
    private final int dimension;

    public OllamaEmbeddingModel(String baseUrl, String model, ObjectMapper mapper) {
        this.mapper = mapper;
        this.model = model;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(2));
        rf.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(rf).baseUrl(baseUrl)
                .defaultHeader("content-type", "application/json").build();
        this.dimension = embed("dimension probe").length; // discover dim once at construction
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String name() {
        return "ollama:" + model;
    }

    @Override
    public float[] embed(String text) {
        try {
            String raw = client.post().uri("/api/embeddings")
                    .body(Map.of("model", model, "prompt", text == null ? "" : text))
                    .retrieve().body(String.class);
            JsonNode arr = mapper.readTree(raw).path("embedding");
            float[] vec = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                vec[i] = (float) arr.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            throw new IllegalStateException("Ollama embeddings failed: " + e.getMessage(), e);
        }
    }
}
