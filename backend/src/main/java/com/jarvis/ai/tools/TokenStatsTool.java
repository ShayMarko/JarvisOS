package com.jarvis.ai.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.observability.ObservabilityService;
import com.jarvis.revenue.RevenueService;

import lombok.RequiredArgsConstructor;

/**
 * Lets the agent answer "what are my token stats / how much have I spent / what's my ROI?" from ANY
 * surface — Discord, voice, chat — not just the client dashboard. Wraps the same observability +
 * revenue read-models the HUD windows use, and returns a compact human-readable summary (no AI cost).
 */
@Component
@RequiredArgsConstructor
public class TokenStatsTool implements Tool {

    private final ObservabilityService observability;
    private final RevenueService revenue;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("token_stats",
                "Report token usage, AI spend/cost, and ROI (does Jarvis out-earn its running cost). "
                + "Use this whenever asked about tokens, usage, cost, spend, budget, or ROI — works over "
                + "Discord/voice/chat. Optional 'limit' = how many recent runs to summarise (default 200).",
                "{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\","
                + "\"description\":\"How many recent AI runs to summarise (default 200).\"}}}");
    }

    @Override
    public String execute(String argumentsJson) {
        int limit = 200;
        // ObjectMapper-free tiny parse: only one optional integer field; default if absent/garbled.
        if (argumentsJson != null && argumentsJson.contains("limit")) {
            String digits = argumentsJson.replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                try {
                    limit = Math.max(1, Math.min(2000, Integer.parseInt(digits)));
                } catch (NumberFormatException ignored) {
                    limit = 200;
                }
            }
        }

        Map<String, Object> cost = observability.costSummary(limit);
        Map<String, Object> roi = revenue.roi();

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Token & cost stats (last ").append(cost.get("runs")).append(" AI runs)\n");
        sb.append("• Tokens: ").append(cost.get("totalTokens")).append(" total (")
          .append(cost.get("promptTokens")).append(" in · ").append(cost.get("completionTokens")).append(" out)\n");
        sb.append("• Est. spend: $").append(cost.get("totalCost")).append(" (paid providers only)\n");

        Object byModel = cost.get("costByModel");
        if (byModel instanceof Map<?, ?> m && !m.isEmpty()) {
            sb.append("• By model: ");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(e.getKey()).append(" $").append(e.getValue());
                first = false;
            }
            sb.append('\n');
        }

        sb.append("\n💰 ROI (").append(roi.get("period")).append(")\n");
        sb.append("• Value generated: $").append(roi.get("valueGenerated"))
          .append("  vs  monthly cost: $").append(roi.get("monthlyCost")).append('\n');
        sb.append("• ROI: ").append(roi.get("roi")).append("×  — ")
          .append(Boolean.TRUE.equals(roi.get("coversCost"))
                  ? "Jarvis is paying for itself ✅"
                  : "not yet covering its cost").append('\n');
        return sb.toString();
    }
}
