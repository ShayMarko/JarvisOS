package com.jarvis.kb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the active {@link EmbeddingModel}. Default: offline hashing-TF. */
@Configuration
public class EmbeddingConfig {

    @Bean
    EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }
}
