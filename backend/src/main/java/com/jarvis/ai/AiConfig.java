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
    LanguageModel languageModel(JarvisAiProperties props, ObjectMapper mapper) {
        boolean claude = "claude".equalsIgnoreCase(props.getProvider())
                && props.getAnthropicApiKey() != null
                && !props.getAnthropicApiKey().isBlank();
        if (claude) {
            log.info("Jarvis AI provider: Anthropic ({})", props.getModel());
            return new AnthropicLanguageModel(props, mapper);
        }
        log.info("Jarvis AI provider: mock (offline). Set jarvis.ai.provider=claude + ANTHROPIC_API_KEY for real reasoning.");
        return new MockLanguageModel();
    }
}
