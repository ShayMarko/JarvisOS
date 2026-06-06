package com.jarvis.ai;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

import org.springframework.stereotype.Component;

import com.jarvis.error.Exceptions.PolicyDeniedException;

import lombok.RequiredArgsConstructor;

/**
 * The token/spend governor — a hard safety net for PAID providers (Claude/OpenAI) so a runaway
 * agent loop can't burn the budget. Tracks tokens used today and USD spent this calendar month,
 * enforces {@code jarvis.ai.daily-token-budget} and {@code jarvis.ai.monthly-budget-usd} (0 =
 * unlimited each), and exposes a manual kill-switch (pause). Local Ollama and the offline mock are
 * never metered (free).
 *
 * <p>In-memory + automatic per-day / per-month reset: a guardrail, not accounting. Enforced at the
 * single model chokepoint ({@link ProviderSwitchingLanguageModel}), which feeds {@link #recordCost}
 * the per-call USD computed from the model's price.
 */
@Component
@RequiredArgsConstructor
public class TokenBudget {

    private final JarvisAiProperties props;

    private final AtomicLong tokensToday = new AtomicLong();
    private final DoubleAdder spendThisMonth = new DoubleAdder();
    private volatile LocalDate day = LocalDate.now();
    private volatile YearMonth month = YearMonth.now();
    private volatile boolean paused = false;

    /** Throw if AI is paused, the daily token budget is spent, or the monthly USD cap is reached. */
    public void checkBeforeCall() {
        if (paused) {
            throw new PolicyDeniedException("Jarvis AI is paused (kill-switch). Resume it in Settings.");
        }
        rollover();
        long limit = props.getDailyTokenBudget();
        if (limit > 0 && tokensToday.get() >= limit) {
            throw new PolicyDeniedException("Daily AI token budget reached (" + limit
                    + " tokens). Raise it in Settings, switch to local Ollama, or wait for the daily reset.");
        }
        double monthlyCap = props.getMonthlyBudgetUsd();
        if (monthlyCap > 0 && spendThisMonth.sum() >= monthlyCap) {
            throw new PolicyDeniedException(String.format(
                    "Monthly AI spend cap reached ($%.2f of $%.2f). Paid calls are paused until next month "
                    + "— Jarvis will keep working on the local Ollama model. Raise the cap in Settings if needed.",
                    spendThisMonth.sum(), monthlyCap));
        }
    }

    /** Record tokens after a paid call. */
    public void record(int promptTokens, int completionTokens) {
        rollover();
        tokensToday.addAndGet((long) Math.max(0, promptTokens) + Math.max(0, completionTokens));
    }

    /** Record the USD cost of a paid call (computed from the model's price at the chokepoint). */
    public void recordCost(double usd) {
        if (usd <= 0) {
            return;
        }
        rollover();
        spendThisMonth.add(usd);
    }

    private synchronized void rollover() {
        LocalDate today = LocalDate.now();
        if (!today.equals(day)) {
            day = today;
            tokensToday.set(0);
        }
        YearMonth ym = YearMonth.now();
        if (!ym.equals(month)) {
            month = ym;
            spendThisMonth.reset();
        }
    }

    /**
     * True when the Model Router should conserve — AI is paused, or today's paid-token use OR this
     * month's paid spend has reached 80% of its cap. The router reads this to downshift to free local /
     * cheaper models so a long session can't blow the budget before the hard stop in {@link #checkBeforeCall}.
     */
    public boolean shouldConserve() {
        if (paused) {
            return true;
        }
        rollover();
        long limit = props.getDailyTokenBudget();
        if (limit > 0 && tokensToday.get() >= (long) (limit * 0.8)) {
            return true;
        }
        double monthlyCap = props.getMonthlyBudgetUsd();
        return monthlyCap > 0 && spendThisMonth.sum() >= monthlyCap * 0.8;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Live snapshot for the UI meter. */
    public Map<String, Object> snapshot() {
        rollover();
        long limit = props.getDailyTokenBudget();
        long used = tokensToday.get();
        double monthlyCap = props.getMonthlyBudgetUsd();
        double spent = spendThisMonth.sum();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paused", paused);
        out.put("dailyTokenBudget", limit);            // 0 = unlimited
        out.put("tokensToday", used);
        out.put("remaining", limit > 0 ? Math.max(0, limit - used) : -1);   // -1 = unlimited
        out.put("monthlyBudgetUsd", monthlyCap);       // 0 = unlimited
        out.put("spentThisMonthUsd", Math.round(spent * 100.0) / 100.0);
        out.put("remainingUsd", monthlyCap > 0 ? Math.max(0, Math.round((monthlyCap - spent) * 100.0) / 100.0) : -1);
        return out;
    }
}
