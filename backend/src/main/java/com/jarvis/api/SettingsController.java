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
import com.jarvis.ai.TokenBudget;
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

    public record BudgetRequest(Long dailyTokenBudget, Double monthlyBudgetUsd, Boolean paused) {}

    private final JarvisAiProperties ai;
    private final TokenBudget budget;
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
        out.put("budget", budget.snapshot());
        return out;
    }

    @PostMapping("/provider")
    public Map<String, Object> setProvider(@RequestBody ProviderRequest req) {
        String provider = req.provider() == null ? ai.getProvider() : req.provider().toLowerCase();
        if (req.provider() != null && !req.provider().isBlank()) {
            ai.setProvider(provider);
        }
        // Route the model name to the field that matches the provider so each provider
        // keeps its own model (openai-model vs ollama-model vs Anthropic model).
        if (req.model() != null && !req.model().isBlank()) {
            switch (provider) {
                case "openai" -> ai.setOpenaiModel(req.model());
                case "ollama" -> ai.setOllamaModel(req.model());
                default -> ai.setModel(req.model());   // claude / anthropic / mock
            }
        }
        audit.record("SETTINGS", "set_provider", ai.getProvider(), "OK", null);
        return get();
    }

    /** Set the daily paid-token budget, the monthly USD spend cap (0 = unlimited each), and/or the kill-switch. */
    @PostMapping("/budget")
    public Map<String, Object> setBudget(@RequestBody BudgetRequest req) {
        if (req.dailyTokenBudget() != null && req.dailyTokenBudget() >= 0) {
            ai.setDailyTokenBudget(req.dailyTokenBudget());
        }
        if (req.monthlyBudgetUsd() != null && req.monthlyBudgetUsd() >= 0) {
            ai.setMonthlyBudgetUsd(req.monthlyBudgetUsd());
        }
        if (req.paused() != null) {
            budget.setPaused(req.paused());
        }
        audit.record("SETTINGS", "set_budget",
                "tokens/day=" + ai.getDailyTokenBudget() + "; usd/month=" + ai.getMonthlyBudgetUsd()
                        + "; paused=" + budget.isPaused(), "OK", null);
        return get();
    }
}
