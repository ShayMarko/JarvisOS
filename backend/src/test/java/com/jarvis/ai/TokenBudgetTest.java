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
