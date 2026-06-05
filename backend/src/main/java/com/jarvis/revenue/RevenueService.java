package com.jarvis.revenue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.jarvis.common.Ids;
import com.jarvis.common.Numbers;

import org.springframework.stereotype.Service;

import com.jarvis.observability.AgentRunRecord;
import com.jarvis.observability.AgentRunRepository;

import lombok.RequiredArgsConstructor;

/**
 * The RevenueOS ledger + ROI engine (from the Revenue strategy): does Jarvis generate more measurable
 * value this month than it costs to run? Sums logged revenue/savings/hours/assets/experiments against
 * the month's real AI cost (from the observability store) plus the fixed monthly subscription.
 */
@Service
@RequiredArgsConstructor
public class RevenueService {

    private final RevenueRepository ledger;
    private final AgentRunRepository runs;
    private final JarvisRevenueProperties props;

    /** Log a ledger entry (income, savings, hours, an asset, or an experiment). */
    public RevenueEntry log(RevenueKind kind, double amount, String note) {
        return ledger.save(new RevenueEntry(
                Ids.generate("rev"), kind, amount, note));
    }

    /** This month's ROI snapshot for the dashboard. */
    public Map<String, Object> roi() {
        Instant monthStart = LocalDate.now(ZoneId.systemDefault())
                .withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        double revenue = 0;
        double saved = 0;
        double hours = 0;
        int assets = 0;
        int experiments = 0;
        for (RevenueEntry e : ledger.findByOccurredAtAfter(monthStart)) {
            switch (e.getKind()) {
                case REVENUE -> revenue += e.getAmount();
                case SAVED -> saved += e.getAmount();
                case HOURS -> hours += e.getAmount();
                case ASSET -> assets += (int) Math.max(1, e.getAmount());
                case EXPERIMENT -> experiments += (int) Math.max(1, e.getAmount());
            }
        }
        double aiCost = 0;
        for (AgentRunRecord r : runs.findByCreatedAtAfter(monthStart)) {
            aiCost += r.getCost();
        }
        double monthlyCost = round(aiCost + props.getMonthlyBaseCostUsd());
        double hoursValue = round(hours * props.getHourlyRate());
        double value = round(revenue + saved + hoursValue);
        double roi = monthlyCost > 0 ? round(value / monthlyCost) : (value > 0 ? value : 0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", "month-to-date");
        out.put("monthlyAiCost", round(aiCost));
        out.put("monthlyBaseCost", round(props.getMonthlyBaseCostUsd()));
        out.put("monthlyCost", monthlyCost);
        out.put("revenue", round(revenue));
        out.put("moneySaved", round(saved));
        out.put("hoursSaved", round(hours));
        out.put("hoursValue", hoursValue);
        out.put("valueGenerated", value);
        out.put("assetsCreated", assets);
        out.put("activeExperiments", experiments);
        out.put("roi", roi);                          // value ÷ cost
        out.put("coversCost", value >= monthlyCost);  // the whole point
        return out;
    }

    /** Convenience for other subsystems to record a created sellable asset. */
    public void logAsset(String name) {
        log(RevenueKind.ASSET, 1, name);
    }

    private static double round(double v) {
        return Numbers.round2(v);
    }
}
