package com.jarvis.brain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvalServiceTest {

    private static final EvalCase CASE = new EvalCase("x", "prompt", "expectation");

    @Test
    void parsesPassVerdict() {
        EvalResult r = EvalService.parseVerdict(CASE, "the answer", "PASS: states 391 correctly");
        assertThat(r.pass()).isTrue();
        assertThat(r.reason()).isEqualTo("states 391 correctly");
        assertThat(r.answer()).isEqualTo("the answer");
    }

    @Test
    void parsesFailVerdict() {
        EvalResult r = EvalService.parseVerdict(CASE, "ans", "FAIL: hallucinated a balance");
        assertThat(r.pass()).isFalse();
        assertThat(r.reason()).isEqualTo("hallucinated a balance");
    }

    @Test
    void blankOrUnknownVerdictFailsClosed() {
        assertThat(EvalService.parseVerdict(CASE, "ans", null).pass()).isFalse();
        assertThat(EvalService.parseVerdict(CASE, "ans", "  ").pass()).isFalse();
        assertThat(EvalService.parseVerdict(CASE, "ans", "maybe it's fine").pass()).isFalse();
    }

    @Test
    void goldenSuiteIsNonEmpty() {
        assertThat(EvalService.DEFAULT_CASES).isNotEmpty();
        assertThat(EvalService.DEFAULT_CASES).allSatisfy(c -> {
            assertThat(c.prompt()).isNotBlank();
            assertThat(c.expectation()).isNotBlank();
        });
    }
}
