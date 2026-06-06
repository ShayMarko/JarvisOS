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
        return route(taskType, null);
    }

    /**
     * Pick the best model for a task, refining the tier by the actual MESSAGE content — so a trivial turn
     * on the catch-all general agent ("what time is it") routes cheap while a hard one ("build me a Spring
     * Boot app") escalates to the strongest model. The biggest cost lever once an expensive model (e.g.
     * Opus) is the brain: most turns hit the general agent, and slug alone can't tell them apart.
     */
    public ModelDescriptor route(String taskType, String message) {
        List<ModelDescriptor> avail = catalog.available();
        // Never route real work to the offline mock when a real model exists.
        List<ModelDescriptor> real = avail.stream().filter(m -> !"mock".equalsIgnoreCase(m.provider())).toList();
        if (!real.isEmpty()) {
            avail = real;
        }
        boolean conserve = budget != null && budget.shouldConserve();
        return choose(avail, props.getPrivacy(), classify(taskType, message), conserve);
    }

    public RoutingPreference preference() {
        return props.getPrivacy();
    }

    /** The best available LOCAL (on-device) model — used by the Privacy Router to keep sensitive
     *  content off cloud providers. Falls back to whatever is available if nothing is local. */
    public ModelDescriptor localModel() {
        List<ModelDescriptor> local = catalog.available().stream().filter(ModelDescriptor::local).toList();
        if (local.isEmpty()) {
            return catalog.available().stream().findFirst().orElse(null);
        }
        return choose(local, RoutingPreference.QUALITY, TaskTier.STANDARD, false);   // strongest local
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

    /** Distinctive substrings that mark a genuinely demanding task → strongest model. */
    private static final String[] HEAVY_HINTS = {
            "build ", "implement", "refactor", "debug", "compile", "stack trace", "algorithm",
            "analyze", "analyse", "research", "optimi", "migrate", "architect", "design a",
            "write a ", "write me a ", "create a ", "create an ", " app", "boilerplate", "saas",
            "step by step", "multi-file", "end to end", "end-to-end"};
    /** Message starts that mark a trivial chore → cheapest capable model. */
    private static final String[] LIGHT_STARTS = {
            "hi", "hey", "hello", "yo ", "thanks", "thank you", "what time", "what's the time",
            "status", "list ", "show me", "how many", "what is my", "what's my", "who am i",
            "remind me", "what day", "what's the date"};

    /**
     * Refine the slug tier by message content (pure + testable). Long/code/"build" messages escalate to
     * HEAVY; very short or chore-like messages on a non-heavy agent drop to LIGHT; everything else keeps
     * the slug's tier. A hint, not a hard gate — STANDARD still scores cost in {@code byBalanced}.
     */
    public static TaskTier classify(String taskType, String message) {
        TaskTier base = tierFor(taskType);
        if (message == null || message.isBlank()) {
            return base;
        }
        String m = message.toLowerCase().strip();
        if (m.length() > 600 || m.contains("```") || containsAny(m, HEAVY_HINTS)) {
            return TaskTier.HEAVY;
        }
        if (base != TaskTier.HEAVY && (m.length() < 24 || startsWithAny(m, LIGHT_STARTS))) {
            return TaskTier.LIGHT;
        }
        return base;
    }

    private static boolean containsAny(String m, String[] hints) {
        for (String h : hints) {
            if (m.contains(h)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithAny(String m, String[] starts) {
        for (String s : starts) {
            if (m.startsWith(s)) {
                return true;
            }
        }
        return false;
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
