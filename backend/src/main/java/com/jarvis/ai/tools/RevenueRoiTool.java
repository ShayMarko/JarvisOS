package com.jarvis.ai.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.revenue.RevenueService;

import lombok.RequiredArgsConstructor;

/** Reports the RevenueOS ROI dashboard — "is Jarvis paying for itself this month?". */
@Component
@RequiredArgsConstructor
public class RevenueRoiTool implements Tool {

    private final RevenueService revenue;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("revenue_roi",
                "Show this month's ROI: AI cost vs. revenue, money saved, hours saved, assets created, and whether "
                + "Jarvis is covering its cost. Use for 'what's my ROI' / 'is Jarvis paying for itself'.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String args) {
        Map<String, Object> r = revenue.roi();
        return "💰 Jarvis ROI (month-to-date)\n"
                + "• Cost: $" + r.get("monthlyCost") + " (AI $" + r.get("monthlyAiCost") + " + base $" + r.get("monthlyBaseCost") + ")\n"
                + "• Revenue: $" + r.get("revenue") + " · Saved: $" + r.get("moneySaved")
                + " · Hours: " + r.get("hoursSaved") + " (=$" + r.get("hoursValue") + ")\n"
                + "• Value generated: $" + r.get("valueGenerated") + " · Assets: " + r.get("assetsCreated")
                + " · Experiments: " + r.get("activeExperiments") + "\n"
                + "• ROI: " + r.get("roi") + "× — " + (Boolean.TRUE.equals(r.get("coversCost"))
                        ? "✅ covering its cost" : "⚠️ not yet covering its cost");
    }
}
