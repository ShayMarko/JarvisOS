package com.jarvis.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A {@link LanguageModel} that picks its backing provider live, per call, from
 * {@link JarvisAiProperties#getProvider()}. This lets the UI switch providers at
 * runtime (Settings / the provider toggle) without a restart. When a paid
 * provider call fails transiently (network/rate-limit/overload) it falls back to
 * the local Ollama model; the offline mock is only the last resort if Ollama is
 * unreachable too — so the agent loop never breaks. A bad API key (401/403) is
 * never masked: it surfaces so the user can fix the config.
 */
public class ProviderSwitchingLanguageModel implements LanguageModel {

    private static final Logger log = LoggerFactory.getLogger(ProviderSwitchingLanguageModel.class);

    private final JarvisAiProperties props;
    private final ObjectMapper mapper;
    private final TokenBudget budget;
    private final MockLanguageModel mock = new MockLanguageModel();
    private volatile AnthropicLanguageModel anthropic;
    private volatile String anthropicKey;
    private volatile OllamaLanguageModel ollama;
    private volatile OpenAiLanguageModel openai;
    private volatile String openaiKey;

    public ProviderSwitchingLanguageModel(JarvisAiProperties props, ObjectMapper mapper, TokenBudget budget) {
        this.props = props;
        this.mapper = mapper;
        this.budget = budget;
    }

    @Override
    public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools) {
        return generate(messages, tools, null);
    }

    @Override
    public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools, String modelOverride) {
        return runOn(active(), messages, tools, modelOverride);
    }

    /**
     * Run on a specific provider + model id, as decided per task by the Model Router. Resolves the
     * adapter for {@code provider} (falling back to the configured active provider if that one isn't
     * usable), then routes the model id to it — so heavy tasks can hit Claude/OpenAI while light ones
     * stay on local Ollama, all within one call. Same metering + fallback as {@link #generate}.
     */
    @Override
    public ModelResponse generateOn(String provider, String modelId,
                                    List<ChatMessage> messages, List<ToolSpec> tools) {
        LanguageModel adapter = adapterFor(provider);
        return runOn(adapter == null ? active() : adapter, messages, tools, modelId);
    }

    /** The usable adapter for a provider name, or {@code null} if it isn't configured/keyed. */
    private LanguageModel adapterFor(String provider) {
        String p = provider == null ? "" : provider.toLowerCase();
        return switch (p) {
            case "claude", "anthropic" -> hasKey() ? anthropic() : null;
            case "openai" -> hasOpenAiKey() ? openai() : null;
            case "ollama" -> ollama();
            case "mock" -> mock;
            default -> null;
        };
    }

    private ModelResponse runOn(LanguageModel m, List<ChatMessage> messages, List<ToolSpec> tools, String modelOverride) {
        // Only meter the real paid adapters; local Ollama + offline mock are free.
        boolean metered = m instanceof AnthropicLanguageModel || m instanceof OpenAiLanguageModel;
        if (metered) {
            budget.checkBeforeCall();   // kill-switch + daily cap (throws if exceeded)
        }
        // Try the chosen provider, retrying transient failures (429/529/5xx/network) with exponential
        // back-off before giving up. A bad API key (401/403) is never retried — it surfaces immediately.
        RuntimeException lastError = null;
        int attempts = (m == mock) ? 1 : MAX_ATTEMPTS;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                ModelResponse resp = m.generate(messages, tools, modelOverride);
                if (metered) {
                    budget.record(resp.promptTokens(), resp.completionTokens());
                }
                return resp;
            } catch (RuntimeException e) {
                if (isAuthError(e)) {
                    throw new IllegalStateException(m.name()
                            + " rejected the request — check the API key (and that the model name is valid).", e);
                }
                lastError = e;
                if (attempt < attempts && isTransient(e)) {
                    long wait = backoffMs(attempt);
                    log.warn("Provider {} transient failure ({}); retry {}/{} in {}ms.",
                            m.name(), e.getMessage(), attempt, attempts - 1, wait);
                    sleep(wait);
                    continue;
                }
                break;   // non-transient, or retries exhausted → fall back below
            }
        }
        // Retries exhausted (or a non-transient failure). Fall back to LOCAL OLLAMA first — we don't like
        // the mock; it's only the last resort if Ollama is unreachable too.
        String why = lastError == null ? "?" : lastError.getMessage();
        LanguageModel local = ollama();
        if (m != local && m != mock) {
            log.warn("Provider {} failed ({}); falling back to local Ollama.", m.name(), why);
            try {
                return local.generate(messages, tools, modelOverride);
            } catch (RuntimeException oe) {
                log.warn("Ollama fallback also failed ({}); using the offline mock as a last resort.", oe.getMessage());
                return mock.generate(messages, tools);
            }
        }
        if (m == local) {
            log.warn("Local Ollama failed ({}); using the offline mock as a last resort.", why);
            return mock.generate(messages, tools);
        }
        throw lastError != null ? lastError : new IllegalStateException("Model call failed");
    }

    /** Max attempts (1 try + retries) against a real provider before falling back. */
    private static final int MAX_ATTEMPTS = 3;

    /** True if the failure chain is an HTTP 401/403 (auth/permission) — a config error. */
    private static boolean isAuthError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.springframework.web.client.HttpClientErrorException http) {
                int code = http.getStatusCode().value();
                if (code == 401 || code == 403) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Transient failure worth retrying: 429 (rate limit), 529 (overloaded), any 5xx, or a network error. */
    private static boolean isTransient(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.springframework.web.client.ResourceAccessException
                    || t instanceof java.io.IOException) {
                return true;   // connection reset / timeout / DNS
            }
            if (t instanceof org.springframework.web.client.RestClientResponseException http) {
                int code = http.getStatusCode().value();
                if (code == 429 || code == 529 || code >= 500) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long backoffMs(int attempt) {
        return 400L * (1L << (attempt - 1));   // 400ms, 800ms, …
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
