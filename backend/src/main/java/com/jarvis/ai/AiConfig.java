package com.jarvis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

/** Selects the active {@link LanguageModel} from {@code jarvis.ai} config. */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * Ensure a JSON mapper bean exists for tool-argument parsing, adapters and the SSE streams.
     * Registers JavaTimeModule explicitly so {@code java.time.Instant} (e.g. an ApprovalRequest's
     * createdAt) serialises as an ISO string instead of throwing on the {@code /api/input/stream} path.
     * (findAndRegisterModules() is unreliable under Spring Boot's classloader, so we register directly.)
     */
    @Bean
    @ConditionalOnMissingBean
    ObjectMapper objectMapper() {
        SimpleModule timeModule = new SimpleModule();
        timeModule.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider sp) throws IOException {
                gen.writeString(value.toString());   // ISO-8601, e.g. 2026-06-14T19:37:42Z
            }
        });
        return new ObjectMapper().registerModule(timeModule);
    }

    @Bean
    LanguageModel languageModel(JarvisAiProperties props, ObjectMapper mapper, TokenBudget budget,
                                com.jarvis.model.ModelCatalog catalog) {
        log.info("Jarvis AI provider: {} (switchable at runtime via /api/settings).", props.getProvider());
        // Live-switching adapter: chooses its backing provider per call from config.
        return new ProviderSwitchingLanguageModel(props, mapper, budget, catalog);
    }
}
