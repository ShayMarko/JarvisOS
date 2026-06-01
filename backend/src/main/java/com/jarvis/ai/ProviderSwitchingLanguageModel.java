package com.jarvis.ai;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A {@link LanguageModel} that picks its backing provider live, per call, from
 * {@link JarvisAiProperties#getProvider()}. This lets the UI switch providers at
 * runtime (Settings / the provider toggle) without a restart. When the chosen
 * provider isn't usable (e.g. "claude" with no API key) it falls back to the
 * offline mock so the agent loop never breaks.
 */
public class ProviderSwitchingLanguageModel implements LanguageModel {

    private final JarvisAiProperties props;
    private final ObjectMapper mapper;
    private final MockLanguageModel mock = new MockLanguageModel();
    private volatile AnthropicLanguageModel anthropic;
    private volatile String anthropicKey;

    public ProviderSwitchingLanguageModel(JarvisAiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools) {
        return active().generate(messages, tools);
    }

    @Override
    public String name() {
        return active().name();
    }

    private LanguageModel active() {
        String provider = props.getProvider() == null ? "" : props.getProvider().toLowerCase();
        boolean wantsAnthropic = provider.equals("claude") || provider.equals("anthropic");
        if (wantsAnthropic && hasKey()) {
            return anthropic();
        }
        return mock;
    }

    private boolean hasKey() {
        String key = props.getAnthropicApiKey();
        return key != null && !key.isBlank();
    }

    /** Build (or rebuild if the key changed) the Anthropic adapter lazily. */
    private AnthropicLanguageModel anthropic() {
        String key = props.getAnthropicApiKey();
        if (anthropic == null || !key.equals(anthropicKey)) {
            anthropic = new AnthropicLanguageModel(props, mapper);
            anthropicKey = key;
        }
        return anthropic;
    }
}
