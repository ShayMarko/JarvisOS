package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.jarvis.error.Exceptions.PolicyDeniedException;

class TokenBudgetTest {

    private TokenBudget budget(long limit) {
        JarvisAiProperties props = new JarvisAiProperties();
        props.setDailyTokenBudget(limit);
        return new TokenBudget(props);
    }

    private TokenBudget monthlyBudget(double usdCap) {
        JarvisAiProperties props = new JarvisAiProperties();
        props.setMonthlyBudgetUsd(usdCap);
        return new TokenBudget(props);
    }

    @Test
    void blocksOnceMonthlyUsdCapIsReached() {
        TokenBudget b = monthlyBudget(80.0);
        b.checkBeforeCall();        // fine at $0
        b.recordCost(79.50);
        b.checkBeforeCall();        // still under $80
        b.recordCost(1.00);         // now $80.50 >= $80
        assertThatThrownBy(b::checkBeforeCall)
                .isInstanceOf(PolicyDeniedException.class)
                .hasMessageContaining("Monthly AI spend cap");
    }

    @Test
    void conservesAtEightyPercentOfMonthlyCap() {
        TokenBudget b = monthlyBudget(80.0);
        b.recordCost(60.0);                 // 75% — not yet
        assertThat(b.shouldConserve()).isFalse();
        b.recordCost(5.0);                  // $65 = 81.25% of $80
        assertThat(b.shouldConserve()).isTrue();
    }

    @Test
    void monthlyCapSnapshotReportsSpendAndRemaining() {
        TokenBudget b = monthlyBudget(80.0);
        b.recordCost(12.34);
        assertThat(b.snapshot().get("monthlyBudgetUsd")).isEqualTo(80.0);
        assertThat(b.snapshot().get("spentThisMonthUsd")).isEqualTo(12.34);
        assertThat(b.snapshot().get("remainingUsd")).isEqualTo(67.66);
    }

    @Test
    void unlimitedNeverBlocksAndReportsMinusOneRemaining() {
        TokenBudget b = budget(0);
        b.record(1000, 1000);
        b.checkBeforeCall();   // must not throw
        assertThat(b.snapshot().get("remaining")).isEqualTo(-1L);
        assertThat(b.snapshot().get("tokensToday")).isEqualTo(2000L);
    }

    @Test
    void blocksOnceTheDailyBudgetIsSpent() {
        TokenBudget b = budget(1500);
        b.checkBeforeCall();      // fine at 0
        b.record(1000, 600);      // 1600 >= 1500
        assertThatThrownBy(b::checkBeforeCall)
                .isInstanceOf(PolicyDeniedException.class)
                .hasMessageContaining("Daily AI token budget");
    }

    @Test
    void killSwitchPausesImmediately() {
        TokenBudget b = budget(0);
        b.setPaused(true);
        assertThatThrownBy(b::checkBeforeCall)
                .isInstanceOf(PolicyDeniedException.class)
                .hasMessageContaining("paused");
        b.setPaused(false);
        b.checkBeforeCall();   // resumed
    }
}
