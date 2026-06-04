package com.jarvis.revenue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jarvis.observability.AgentRunRecord;
import com.jarvis.observability.AgentRunRepository;

class RevenueServiceTest {

    private RevenueService service(List<RevenueEntry> ledger, double aiCost, double hourly, double base) {
        RevenueRepository repo = mock(RevenueRepository.class);
        when(repo.findByOccurredAtAfter(any())).thenReturn(ledger);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunRecord run = mock(AgentRunRecord.class);
        when(run.getCost()).thenReturn(aiCost);
        when(runs.findByCreatedAtAfter(any())).thenReturn(List.of(run));
        JarvisRevenueProperties p = new JarvisRevenueProperties();
        p.setHourlyRate(hourly);
        p.setMonthlyBaseCostUsd(base);
        return new RevenueService(repo, runs, p);
    }

    private RevenueEntry entry(RevenueKind kind, double amount) {
        return new RevenueEntry("id", kind, amount, null);
    }

    @Test
    void computesValueCostAndRoi() {
        RevenueService svc = service(List.of(
                entry(RevenueKind.REVENUE, 29),
                entry(RevenueKind.SAVED, 10),
                entry(RevenueKind.HOURS, 2),         // ×$50 = $100
                entry(RevenueKind.ASSET, 1),
                entry(RevenueKind.EXPERIMENT, 1)),
                /*aiCost*/ 3, /*hourly*/ 50, /*base*/ 30);

        Map<String, Object> r = svc.roi();
        assertThat(r.get("monthlyCost")).isEqualTo(33.0);            // 3 AI + 30 base
        assertThat(r.get("hoursValue")).isEqualTo(100.0);
        assertThat(r.get("valueGenerated")).isEqualTo(139.0);        // 29 + 10 + 100
        assertThat(r.get("assetsCreated")).isEqualTo(1);
        assertThat(r.get("activeExperiments")).isEqualTo(1);
        assertThat(r.get("coversCost")).isEqualTo(true);             // 139 ≥ 33
    }

    @Test
    void notCoveringWhenValueBelowCost() {
        RevenueService svc = service(List.of(entry(RevenueKind.REVENUE, 5)), 2, 50, 30);
        Map<String, Object> r = svc.roi();
        assertThat(r.get("coversCost")).isEqualTo(false);            // 5 < 32
    }
}
