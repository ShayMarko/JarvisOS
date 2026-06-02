package com.jarvis.ai;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.jarvis.error.Exceptions.PolicyDeniedException;

import lombok.RequiredArgsConstructor;

/**
 * The token spend governor — a hard safety net for PAID providers (Claude/OpenAI) so
 * a runaway agent loop can't burn tokens fast. Tracks tokens used today (prompt+completion),
 * enforces {@code jarvis.ai.daily-token-budget} (0 = unlimited), and exposes a manual
 * kill-switch (pause). Local Ollama and the offline mock are never metered (free).
 *
 * <p>In-memory + per-day reset: a guardrail, not accounting. Cost is computed for display
 * only. Enforced at the single model chokepoint ({@link ProviderSwitchingLanguageModel}).
 */
@Component
@RequiredArgsConstructor
public class TokenBudget {

    private final JarvisAiProperties props;

    private final AtomicLong tokensToday = new AtomicLong();
    private volatile LocalDate day = LocalDate.now();
    private volatile boolean paused = false;

    /** Throw if AI is paused or the daily budget is already spent. Call before a paid model call. */
    public void checkBeforeCall() {
        if (paused) {
            throw new PolicyDeniedException("Jarvis AI is paused (token kill-switch). Resume it in Settings.");
        }
        rollover();
        long limit = props.getDailyTokenBudget();
        if (limit > 0 && tokensToday.get() >= limit) {
            throw new PolicyDeniedException("Daily AI token budget reached (" + limit
                    + " tokens). Raise it in Settings, switch to local Ollama, or wait for the daily reset.");
        }
    }

    /** Record tokens after a paid call. */
    public void record(int promptTokens, int completionTokens) {
        rollover();
        tokensToday.addAndGet((long) Math.max(0, promptTokens) + Math.max(0, completionTokens));
    }

    private synchronized void rollover() {
        LocalDate today = LocalDate.now();
        if (!today.equals(day)) {
            day = today;
            tokensToday.set(0);
        }
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
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paused", paused);
        out.put("dailyTokenBudget", limit);            // 0 = unlimited
        out.put("tokensToday", used);
        out.put("remaining", limit > 0 ? Math.max(0, limit - used) : -1);   // -1 = unlimited
        return out;
    }
}
