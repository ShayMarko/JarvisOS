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

    /** "mock" (default, offline) or "claude". */
    private String provider = "mock";
    private String anthropicApiKey = "";
    private String model = "claude-opus-4-8";
    private int maxTokens = 1024;
    /** Max tool-calling iterations before the runtime gives up. */
    private int maxSteps = 4;
    /** Model Router preference (spec §6): BALANCED | PRIVATE | QUALITY | CHEAP. */
    private RoutingPreference privacy = RoutingPreference.BALANCED;
}
