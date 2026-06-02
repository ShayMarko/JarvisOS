package com.jarvis.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A {@link LanguageModel} that picks its backing provider live, per call, from
 * {@link JarvisAiProperties#getProvider()}. This lets the UI switch providers at
 * runtime (Settings / the provider toggle) without a restart. When the chosen
 * provider isn't usable (e.g. "claude" with no API key) it falls back to the
 * offline mock so the agent loop never breaks.
 */
public class ProviderSwitchingLanguageModel implements LanguageModel {

    private static final Logger log = LoggerFactory.getLogger(ProviderSwitchingLanguageModel.class);

    private final JarvisAiProperties props;
    private final ObjectMapper mapper;
    private final MockLanguageModel mock = new MockLanguageModel();
    private volatile AnthropicLanguageModel anthropic;
    private volatile String anthropicKey;
    private volatile OllamaLanguageModel ollama;
    private volatile OpenAiLanguageModel openai;
    private volatile String openaiKey;

    public ProviderSwitchingLanguageModel(JarvisAiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools) {
        LanguageModel m = active();
        try {
            return m.generate(messages, tools);
        } catch (RuntimeException e) {
            if (m != mock) {
                log.warn("Provider {} failed ({}); falling back to the offline mock.", m.name(), e.getMessage());
                return mock.generate(messages, tools);
            }
            throw e;
        }
    }

    @Override
    public String name() {
        return active().name();
    }

    private LanguageModel active() {
        String provider = props.getProvider() == null ? "" : props.getProvider().toLowerCase();
        if ((provider.equals("claude") || provider.equals("anthropic")) && hasKey()) {
            return anthropic();
        }
        if (provider.equals("ollama")) {
            return ollama();
        }
        if (provider.equals("openai") && hasOpenAiKey()) {
            return openai();
        }
        return mock;
    }

    private OllamaLanguageModel ollama() {
        if (ollama == null) {
            ollama = new OllamaLanguageModel(props, mapper);
        }
        return ollama;
    }

    private boolean hasOpenAiKey() {
        String key = props.getOpenaiApiKey();
        return key != null && !key.isBlank();
    }

    /** Build (or rebuild if the key changed) the OpenAI adapter lazily. */
    private OpenAiLanguageModel openai() {
        String key = props.getOpenaiApiKey();
        if (openai == null || !key.equals(openaiKey)) {
            openai = new OpenAiLanguageModel(props, mapper);
            openaiKey = key;
        }
        return openai;
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
