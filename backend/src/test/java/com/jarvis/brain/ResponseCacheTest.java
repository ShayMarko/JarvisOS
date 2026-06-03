package com.jarvis.brain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResponseCacheTest {

    private static final long T = 1_000_000L;

    @Test
    void cachesAndNormalizesTimelessQuestions() {
        ResponseCache c = new ResponseCache();
        c.put("what is recursion in programming", "Recursion is when a function calls itself.", T);
        assertThat(c.lookup("what is recursion in programming", T + 1000).answer())
                .isEqualTo("Recursion is when a function calls itself.");
        // case + whitespace normalized to the same key
        assertThat(c.lookup("WHAT  is   Recursion in programming", T + 1000).answer())
                .isEqualTo("Recursion is when a function calls itself.");
    }

    @Test
    void reportsAgeOfACachedAnswer() {
        ResponseCache c = new ResponseCache();
        c.put("what is a monad in functional programming", "A monad is...", T);
        assertThat(c.lookup("what is a monad in functional programming", T + 90_000).ageSeconds()).isEqualTo(90);
    }

    @Test
    void expiresAfterTtl() {
        ResponseCache c = new ResponseCache();
        c.put("define idempotency clearly", "It means repeating is safe.", T);
        assertThat(c.lookup("define idempotency clearly", T + 6 * 60 * 1000)).isNull();   // > 5 min TTL
    }

    @Test
    void doesNotCacheContextDependentOrTinyMessages() {
        ResponseCache c = new ResponseCache();
        c.put("tell me more about it", "stuff", T);          // "it"/"more" → context-dependent
        assertThat(c.lookup("tell me more about it", T + 1000)).isNull();
        c.put("hi", "hello", T);                              // too short
        assertThat(c.lookup("hi", T + 1000)).isNull();
    }
}
