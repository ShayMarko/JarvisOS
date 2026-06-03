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

    /** "mock" (offline), "claude" (Anthropic), "ollama" (local), or "openai". */
    private String provider = "mock";
    private String anthropicApiKey = "";
    private String model = "claude-opus-4-8";
    /** Local Ollama server + chat/embedding models (real reasoning, no API key). */
    private String ollamaBaseUrl = "http://localhost:11434";
    private String ollamaModel = "qwen2.5-coder:14b";
    private String ollamaEmbeddingModel = "nomic-embed-text";
    /** HTTP read timeout for an Ollama generation (seconds). Big models building multi-file projects
     *  can take minutes; too low and the call fails and silently falls back to the mock. */
    private int ollamaTimeoutSeconds = 300;
    /** Max output tokens per LOCAL Ollama generation (num_predict). Local is free, and writing a real
     *  file needs far more than the paid-provider {@code maxTokens} cap — too low truncates the file
     *  (and its tool-call JSON) mid-stream. -1 = until context end. */
    private int ollamaNumPredict = 8192;
    /** Privacy Router: when true, sensitive content (secrets/financial/personal/confidential) is kept on
     *  the LOCAL model instead of being sent to a cloud provider. Detection is local-only, so it never
     *  leaks the content it's protecting. */
    private boolean privacyGuard = true;
    /** Self-correcting build loop: after a build writes files, the Test agent verifies (run_in_sandbox)
     *  and, on failure, the developer is re-dispatched with the errors, then re-verified — up to this
     *  many fix attempts. 0 = off. Shines on a capable model; under-converges on a small local one. */
    private int buildVerifyMaxIters = 2;
    /** OpenAI (Chat Completions API). Needs an API key. */
    private String openaiApiKey = "";
    private String openaiBaseUrl = "https://api.openai.com/v1";
    private String openaiModel = "gpt-4o-mini";
    /** Cheap models used for the lightweight PLANNER call (it only emits a tiny JSON plan),
     *  so planning doesn't burn the expensive main model. Per paid provider. */
    private String plannerModelClaude = "claude-3-5-haiku-latest";
    private String plannerModelOpenai = "gpt-4o-mini";
    private int maxTokens = 1024;
    /** Max tool-calling iterations per agent run before the runtime gives up. Higher lets an
     *  agent write a whole multi-file project (one write_file per step); simple turns still end
     *  early, so raising the cap costs nothing for them. */
    private int maxSteps = 12;
    /** Safety cap on PAID-provider tokens per day (prompt+completion). 0 = unlimited.
     *  Local (ollama) and mock are never metered. */
    private long dailyTokenBudget = 0;
    /** Model Router preference (spec §6): BALANCED | PRIVATE | QUALITY | CHEAP. */
    private RoutingPreference privacy = RoutingPreference.BALANCED;
}
