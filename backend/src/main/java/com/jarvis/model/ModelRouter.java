package com.jarvis.model;

import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.TokenBudget;

/**
 * The Model Router (spec §6) — picks a model PER TASK by quality, cost, latency and privacy, then the
 * chosen model is actually executed (see {@code AgentRuntime}/{@code ProviderSwitchingLanguageModel}),
 * not just displayed. Choice is real: it only ever selects among models that are wired up right now.
 *
 * <p>How the decision is made:
 * <ul>
 *   <li>An explicit {@link RoutingPreference} (QUALITY / CHEAP / PRIVATE) always wins globally.</li>
 *   <li>On BALANCED (the default) the per-task <b>tier</b> decides: heavy reasoning (code/build/debug/
 *       research…) → highest quality; light chores (status/files/calendar…) → cheapest; the rest →
 *       a blended score.</li>
 *   <li>If the daily token budget is nearly spent (or AI is paused), it conserves — prefers free local
 *       models and the cheapest option — so a long session can't blow the cap.</li>
 *   <li>The offline mock is never chosen while any real model is available.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ModelRouter {

    /** How demanding a task is — drives model choice when the preference is BALANCED. */
    public enum TaskTier { HEAVY, STANDARD, LIGHT }

    /** Agents whose work benefits from the strongest model (writing/fixing code, analysis, research). */
    private static final Set<String> HEAVY = Set.of(
            "code", "backend", "frontend", "codefix", "debug", "review", "product",
            "data", "research", "devops", "test", "uiqa");
    /** Agents whose work is light/mechanical — cheapest capable model is fine. */
    private static final Set<String> LIGHT = Set.of(
            "system", "files", "calendar", "email", "memory", "screenshot", "backup", "knowledge");

    private final ModelCatalog catalog;
    private final JarvisAiProperties props;
    private final TokenBudget budget;

    /** Pick the best model for a task (identified by agent slug / task type). */
    public ModelDescriptor route(String taskType) {
        List<ModelDescriptor> avail = catalog.available();
        // Never route real work to the offline mock when a real model exists.
        List<ModelDescriptor> real = avail.stream().filter(m -> !"mock".equalsIgnoreCase(m.provider())).toList();
        if (!real.isEmpty()) {
            avail = real;
        }
        boolean conserve = budget != null && budget.shouldConserve();
        return choose(avail, props.getPrivacy(), tierFor(taskType), conserve);
    }

    public RoutingPreference preference() {
        return props.getPrivacy();
    }

    /** Map an agent slug / task type to its demand tier. */
    public static TaskTier tierFor(String taskType) {
        String t = taskType == null ? "" : taskType.toLowerCase();
        if (HEAVY.contains(t)) {
            return TaskTier.HEAVY;
        }
        if (LIGHT.contains(t)) {
            return TaskTier.LIGHT;
        }
        return TaskTier.STANDARD;
    }

    /** Back-compat: preference-only choice (STANDARD tier, not conserving). */
    public static ModelDescriptor choose(List<ModelDescriptor> available, RoutingPreference pref) {
        return choose(available, pref, TaskTier.STANDARD, false);
    }

    public static ModelDescriptor choose(List<ModelDescriptor> available, RoutingPreference pref, TaskTier tier) {
        return choose(available, pref, tier, false);
    }

    /** Pure routing decision — testable without Spring. */
    public static ModelDescriptor choose(List<ModelDescriptor> available, RoutingPreference pref,
                                         TaskTier tier, boolean conserve) {
        if (available.isEmpty()) {
            return null;
        }
        List<ModelDescriptor> pool = available;
        // PRIVATE (or conserving) keeps work on free, on-device models when any exist.
        if (pref == RoutingPreference.PRIVATE || conserve) {
            List<ModelDescriptor> local = pool.stream().filter(ModelDescriptor::local).toList();
            if (!local.isEmpty()) {
                pool = local;
            }
        }
        Comparator<ModelDescriptor> cmp;
        if (pref == RoutingPreference.QUALITY) {
            cmp = byQuality();
        } else if (pref == RoutingPreference.CHEAP || conserve) {
            cmp = byCheap();
        } else if (pref == RoutingPreference.PRIVATE) {
            cmp = byBalanced();
        } else {
            cmp = switch (tier) {       // BALANCED → the task's demand tier decides
                case HEAVY -> byQuality();
                case LIGHT -> byCheap();
                default -> byBalanced();
            };
        }
        return pool.stream().max(cmp).orElse(pool.get(0));
    }

    private static Comparator<ModelDescriptor> byQuality() {
        return Comparator.comparingInt(ModelDescriptor::quality)
                .thenComparing(Comparator.comparingDouble(ModelRouter::blendedCost).reversed());
    }

    private static Comparator<ModelDescriptor> byCheap() {
        return Comparator.comparingDouble(ModelRouter::blendedCost).reversed()
                .thenComparingInt(ModelDescriptor::quality);
    }

    private static Comparator<ModelDescriptor> byBalanced() {
        return Comparator.comparingDouble(ModelRouter::balancedScore);
    }

    private static double blendedCost(ModelDescriptor m) {
        return (m.costInputPer1k() + m.costOutputPer1k()) / 2;
    }

    /** Higher is better: reward quality, penalise cost and latency, small local bonus. */
    private static double balancedScore(ModelDescriptor m) {
        return m.quality()
                - blendedCost(m) * 20
                - m.latencyMs() / 2000.0
                + (m.local() ? 0.5 : 0);
    }
}
