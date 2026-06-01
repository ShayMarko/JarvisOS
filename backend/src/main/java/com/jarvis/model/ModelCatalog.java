package com.jarvis.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.LanguageModel;

/**
 * The Model Manager's catalogue (spec §6). Lists known models with cost/quality
 * metadata and marks the one actually wired up right now (the active adapter).
 */
@Component
public class ModelCatalog {

    private final List<ModelDescriptor> models = new ArrayList<>();

    public ModelCatalog(LanguageModel active) {
        String activeId = activeId(active.name());

        List<ModelDescriptor> base = List.of(
                new ModelDescriptor("mock-local", "mock", true, 0, 0, 2, 5, false),
                new ModelDescriptor("claude-haiku-4-8", "anthropic", false, 0.0008, 0.004, 3, 600, false),
                new ModelDescriptor("claude-sonnet-4-8", "anthropic", false, 0.003, 0.015, 4, 900, false),
                new ModelDescriptor("claude-opus-4-8", "anthropic", false, 0.015, 0.075, 5, 1500, false));

        boolean matched = false;
        for (ModelDescriptor d : base) {
            boolean avail = d.id().equals(activeId);
            matched |= avail;
            models.add(new ModelDescriptor(d.id(), d.provider(), d.local(), d.costInputPer1k(),
                    d.costOutputPer1k(), d.quality(), d.latencyMs(), avail));
        }
        if (!matched) {
            // configured a model not in the static list — add it as available
            models.add(new ModelDescriptor(activeId, "anthropic", false, 0.003, 0.015, 4, 900, true));
        }
    }

    private static String activeId(String name) {
        return name.startsWith("mock") ? "mock-local" : name.replace("claude:", "");
    }

    public List<ModelDescriptor> all() {
        return List.copyOf(models);
    }

    public List<ModelDescriptor> available() {
        return models.stream().filter(ModelDescriptor::available).toList();
    }

    public ModelDescriptor active() {
        return available().stream().findFirst().orElse(models.get(0));
    }

    public ModelDescriptor byId(String id) {
        return models.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }
}
