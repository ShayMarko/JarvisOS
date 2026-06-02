package com.jarvis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Selects the active {@link LanguageModel} from {@code jarvis.ai} config. */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /** Ensure a JSON mapper bean exists for tool-argument parsing and adapters. */
    @Bean
    @ConditionalOnMissingBean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    LanguageModel languageModel(JarvisAiProperties props, ObjectMapper mapper, TokenBudget budget) {
        log.info("Jarvis AI provider: {} (switchable at runtime via /api/settings).", props.getProvider());
        // Live-switching adapter: chooses its backing provider per call from config.
        return new ProviderSwitchingLanguageModel(props, mapper, budget);
    }
}
