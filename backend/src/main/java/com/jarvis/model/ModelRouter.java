package com.jarvis.model;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.JarvisAiProperties;

/**
 * The Model Router (spec §6) — picks a model per request by privacy, quality and
 * cost. It only ever chooses among models that are actually available, so the
 * choice is real, not advisory. (With the offline mock that's "mock-local"; add
 * an API key and the configured cloud model becomes selectable.)
 */
@Component
public class ModelRouter {

    private final ModelCatalog catalog;
    private final JarvisAiProperties props;

    public ModelRouter(ModelCatalog catalog, JarvisAiProperties props) {
        this.catalog = catalog;
        this.props = props;
    }

    public ModelDescriptor route(String taskType) {
        return choose(catalog.available(), props.getPrivacy());
    }

    public RoutingPreference preference() {
        return props.getPrivacy();
    }

    /** Pure routing decision — testable without Spring. */
    public static ModelDescriptor choose(List<ModelDescriptor> available, RoutingPreference pref) {
        if (available.isEmpty()) {
            return null;
        }
        List<ModelDescriptor> pool = available;
        if (pref == RoutingPreference.PRIVATE) {
            List<ModelDescriptor> local = available.stream().filter(ModelDescriptor::local).toList();
            if (!local.isEmpty()) {
                pool = local; // nothing leaves the machine
            }
        }
        Comparator<ModelDescriptor> comparator = switch (pref) {
            case QUALITY -> Comparator.comparingInt(ModelDescriptor::quality)
                    .thenComparing(Comparator.comparingDouble(ModelRouter::blendedCost).reversed());
            case CHEAP -> Comparator.comparingDouble(ModelRouter::blendedCost).reversed()
                    .thenComparingInt(ModelDescriptor::quality);
            default -> Comparator.comparingDouble(ModelRouter::balancedScore);
        };
        return pool.stream().max(comparator).orElse(pool.get(0));
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
