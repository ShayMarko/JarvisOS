package com.jarvis.kb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.JarvisAiProperties;

/**
 * Selects the active {@link EmbeddingModel}. Prefers local Ollama neural
 * embeddings when reachable at startup (real semantic vectors); otherwise falls
 * back to the offline {@link HashingEmbeddingModel} so the KB always works.
 */
@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    EmbeddingModel embeddingModel(JarvisAiProperties ai, ObjectMapper mapper) {
        try {
            EmbeddingModel ollama = new OllamaEmbeddingModel(ai.getOllamaBaseUrl(), ai.getOllamaEmbeddingModel(), mapper);
            log.info("KB embeddings: {} ({}-dim, neural).", ollama.name(), ollama.dimension());
            return ollama;
        } catch (RuntimeException e) {
            log.info("KB embeddings: offline hashing model (Ollama not reachable — pull '{}' & start Ollama for neural embeddings).",
                    ai.getOllamaEmbeddingModel());
            return new HashingEmbeddingModel();
        }
    }
}
