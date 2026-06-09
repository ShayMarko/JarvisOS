package com.jarvis.brain;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.model.CostCalculator;
import com.jarvis.model.ModelDescriptor;
import com.jarvis.model.ModelRouter;

import lombok.RequiredArgsConstructor;

/**
 * Cost-aware planning: estimates what a task (or a whole multi-step plan) will cost BEFORE running it, by
 * asking the router which model each sub-task would use and pricing a rough token budget. Lets the Brain
 * surface "this job ≈ $X" up front instead of only knowing the bill afterwards. Estimates are deliberately
 * conservative and cheap to compute (no model call). Local/free models estimate to $0.
 */
@Component
@RequiredArgsConstructor
public class CostEstimator {

    /** Rough completion size assumed per sub-task when estimating (big enough not to under-count builds). */
    static final int EST_COMPLETION_TOKENS = 1200;
    /** Fixed prompt overhead (system prompt + tools + context) added to the message's own size. */
    static final int PROMPT_OVERHEAD_TOKENS = 600;

    private final ModelRouter router;

    /** Estimated USD for a single sub-task on the model the router would pick for it. */
    public double estimateTaskCost(String agentSlug, String taskText) {
        ModelDescriptor m = router.route(agentSlug, taskText);
        if (m == null) {
            return 0;
        }
        return CostCalculator.cost(m, estimatePromptTokens(taskText), EST_COMPLETION_TOKENS);
    }

    /** Estimated USD for a whole plan = sum of its sub-tasks. */
    public double estimatePlanCost(List<PlanStep> plan) {
        if (plan == null || plan.isEmpty()) {
            return 0;
        }
        return plan.stream().mapToDouble(ps -> estimateTaskCost(ps.agentSlug(), ps.task())).sum();
    }

    /** ~4 characters per token, plus a fixed prompt overhead (pure + testable). */
    static int estimatePromptTokens(String text) {
        int chars = text == null ? 0 : text.length();
        return PROMPT_OVERHEAD_TOKENS + chars / 4;
    }
}
