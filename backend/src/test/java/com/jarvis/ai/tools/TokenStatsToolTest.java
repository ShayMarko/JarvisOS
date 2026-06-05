package com.jarvis.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.observability.ObservabilityService;
import com.jarvis.revenue.RevenueService;

class TokenStatsToolTest {

    private TokenStatsTool tool(boolean covers) {
        ObservabilityService obs = mock(ObservabilityService.class);
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("runs", 12);
        cost.put("totalTokens", 188869L);
        cost.put("promptTokens", 174588L);
        cost.put("completionTokens", 14281L);
        cost.put("totalCost", 0.738);
        cost.put("costByModel", Map.of("claude:opus", 0.74));
        when(obs.costSummary(anyInt())).thenReturn(cost);

        RevenueService rev = mock(RevenueService.class);
        Map<String, Object> roi = new LinkedHashMap<>();
        roi.put("period", "month-to-date");
        roi.put("valueGenerated", 29.0);
        roi.put("monthlyCost", 0.74);
        roi.put("roi", 39.19);
        roi.put("coversCost", covers);
        when(rev.roi()).thenReturn(roi);

        return new TokenStatsTool(obs, rev, new ObjectMapper());
    }

    @Test
    void summarisesTokensCostAndRoi() {
        String out = tool(true).execute("{}");
        assertThat(out)
                .contains("Token & cost stats")
                .contains("188869")
                .contains("$0.738")
                .contains("39.19")
                .contains("paying for itself");
    }

    @Test
    void reportsWhenNotCoveringCost() {
        assertThat(tool(false).execute("{\"limit\":50}")).contains("not yet covering its cost");
    }
}
