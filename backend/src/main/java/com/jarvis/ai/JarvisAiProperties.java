package com.jarvis.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.jarvis.model.RoutingPreference;

import lombok.Getter;
import lombok.Setter;

/** Binds {@code jarvis.ai} — model provider selection and parameters. */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.ai")
public class JarvisAiProperties {

    /** "mock" (default, offline), "claude" (Anthropic), or "ollama" (local). */
    private String provider = "mock";
    private String anthropicApiKey = "";
    private String model = "claude-opus-4-8";
    /** Local Ollama server + chat/embedding models (real reasoning, no API key). */
    private String ollamaBaseUrl = "http://localhost:11434";
    private String ollamaModel = "llama3.1";
    private String ollamaEmbeddingModel = "nomic-embed-text";
    private int maxTokens = 1024;
    /** Max tool-calling iterations before the runtime gives up. */
    private int maxSteps = 4;
    /** Model Router preference (spec §6): BALANCED | PRIVATE | QUALITY | CHEAP. */
    private RoutingPreference privacy = RoutingPreference.BALANCED;
}
