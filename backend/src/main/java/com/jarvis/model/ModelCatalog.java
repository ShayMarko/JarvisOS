package com.jarvis.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.JarvisAiProperties;

/**
 * The Model Manager's catalogue (spec §6). Lists the models the Model Router may choose from —
 * across ALL configured providers (local Ollama, OpenAI, Anthropic) plus the offline mock — and
 * marks which are actually usable right now (local is always usable; a cloud model needs its API
 * key). The router picks among {@link #available()} so its choice is real, never advisory.
 */
@Component
public class ModelCatalog {

    private final JarvisAiProperties props;
    private final List<ModelDescriptor> models = new ArrayList<>();

    public ModelCatalog(JarvisAiProperties props) {
        this.props = props;
        rebuild();
    }

    /** (Re)build the catalogue from current config — availability follows the keys that are set. */
    private void rebuild() {
        models.clear();
        boolean openai = notBlank(props.getOpenaiApiKey());
        boolean anthropic = notBlank(props.getAnthropicApiKey());

        // Offline stub — only a last resort, so it carries the lowest quality.
        models.add(new ModelDescriptor("mock-local", "mock", true, 0, 0, 1, 5, true));
        // Local Ollama — free + private, always considered usable (if the server is down the
        // runtime falls back to the mock at call time).
        models.add(new ModelDescriptor(props.getOllamaModel(), "ollama", true, 0, 0, 3, 900, true));
        // OpenAI — the single configured chat model (needs a key).
        models.add(new ModelDescriptor(props.getOpenaiModel(), "openai", false, 0.00015, 0.0006, 4, 700, openai));
        // Anthropic — three quality/cost tiers so the router can spend the light model on light work and
        // the heavy model on heavy reasoning (all need a key). Model IDs are yaml-controlled (no hard-codes):
        // LIGHT = planner-model-claude, STANDARD = claude-standard-model, HEAVY = model.
        models.add(new ModelDescriptor(props.getPlannerModelClaude(), "anthropic", false, 0.0008, 0.004, 3, 600, anthropic));
        models.add(new ModelDescriptor(props.getClaudeStandardModel(), "anthropic", false, 0.003, 0.015, 4, 900, anthropic));
        models.add(new ModelDescriptor(props.getModel(), "anthropic", false, 0.015, 0.075, 5, 1500, anthropic));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** The model id for the currently-configured default provider (shown as "active" in the UI). */
    private String activeId() {
        String p = props.getProvider() == null ? "" : props.getProvider().toLowerCase();
        return switch (p) {
            case "ollama" -> props.getOllamaModel();
            case "openai" -> props.getOpenaiModel();
            case "claude", "anthropic" -> props.getModel();
            default -> "mock-local";
        };
    }

    public List<ModelDescriptor> all() {
        rebuild();   // reflect live key/provider changes (Settings can flip them at runtime)
        return List.copyOf(models);
    }

    public List<ModelDescriptor> available() {
        return all().stream().filter(ModelDescriptor::available).toList();
    }

    public ModelDescriptor active() {
        String id = activeId();
        return all().stream().filter(m -> m.id().equals(id) && m.available()).findFirst()
                .orElseGet(() -> available().stream().findFirst().orElse(models.get(0)));
    }

    public ModelDescriptor byId(String id) {
        return all().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }
}
