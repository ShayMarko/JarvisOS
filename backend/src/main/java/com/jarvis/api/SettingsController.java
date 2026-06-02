package com.jarvis.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.audit.AuditService;

import lombok.RequiredArgsConstructor;

/**
 * Runtime settings the UI can read and change — currently the active AI provider
 * and model. Backed by {@link JarvisAiProperties}, which the live-switching
 * language model reads per call, so changes take effect immediately.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    public record ProviderRequest(String provider, String model) {}

    private final JarvisAiProperties ai;
    private final AuditService audit;

    @GetMapping
    public Map<String, Object> get() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", ai.getProvider());
        out.put("model", ai.getModel());
        out.put("hasAnthropicKey", ai.getAnthropicApiKey() != null && !ai.getAnthropicApiKey().isBlank());
        out.put("hasOpenaiKey", ai.getOpenaiApiKey() != null && !ai.getOpenaiApiKey().isBlank());
        out.put("ollamaModel", ai.getOllamaModel());
        out.put("openaiModel", ai.getOpenaiModel());
        // mock = offline stub; claude = Anthropic (needs key); ollama = local model; openai = OpenAI (needs key).
        out.put("providers", List.of("mock", "claude", "ollama", "openai"));
        return out;
    }

    @PostMapping("/provider")
    public Map<String, Object> setProvider(@RequestBody ProviderRequest req) {
        if (req.provider() != null && !req.provider().isBlank()) {
            ai.setProvider(req.provider().toLowerCase());
        }
        if (req.model() != null && !req.model().isBlank()) {
            ai.setModel(req.model());
        }
        audit.record("SETTINGS", "set_provider", ai.getProvider(), "OK", null);
        return get();
    }
}
