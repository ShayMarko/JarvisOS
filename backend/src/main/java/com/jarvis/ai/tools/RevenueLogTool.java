package com.jarvis.ai.tools;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.revenue.RevenueKind;
import com.jarvis.revenue.RevenueService;

import lombok.RequiredArgsConstructor;

/** Records a RevenueOS ledger entry — income, savings, hours, an asset, or an experiment. */
@Component
@RequiredArgsConstructor
public class RevenueLogTool implements Tool {

    private final RevenueService revenue;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("revenue_log",
                "Log money/value into the ROI ledger. 'kind' = revenue|saved|hours|asset|experiment; 'amount' = USD "
                + "(revenue/saved), hours (hours), or count (asset/experiment, default 1); optional 'note'.",
                "{\"type\":\"object\",\"properties\":{\"kind\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"},"
                + "\"note\":{\"type\":\"string\"}},\"required\":[\"kind\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        String kindStr = ToolArgs.firstStr(mapper, args, "kind", "type");
        if (kindStr.isBlank()) {
            return "Provide 'kind' (revenue/saved/hours/asset/experiment).";
        }
        RevenueKind kind;
        try {
            kind = RevenueKind.valueOf(kindStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "Unknown kind '" + kindStr + "'. Use revenue/saved/hours/asset/experiment.";
        }
        double amount = parseAmount(ToolArgs.firstStr(mapper, args, "amount", "value", "usd", "hours"));
        revenue.log(kind, amount, ToolArgs.firstStr(mapper, args, "note", "label", "source"));
        return "Logged " + kind + " " + amount + ". Ask 'what's my ROI' to see the dashboard.";
    }

    private static double parseAmount(String s) {
        try {
            return s == null || s.isBlank() ? 1 : Double.parseDouble(s.trim().replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
