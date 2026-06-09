package com.jarvis.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** The conservative near-identical matching that decides when a saved plan is safe to replay. */
class PlaybookServiceTest {

    @Test
    void normalizeStripsPunctuationAndCase() {
        assertThat(PlaybookService.normalize("Build me a Spring-Boot API!!"))
                .isEqualTo("build me a spring boot api");
    }

    @Test
    void identicalIntentMatchesAboveThreshold() {
        Set<String> a = PlaybookService.tokens("build me a spring boot rest api with auth");
        Set<String> b = PlaybookService.tokens("build me a spring boot rest api with auth");
        assertThat(PlaybookService.jaccard(a, b)).isEqualTo(1.0);
    }

    @Test
    void differentRequestsStayBelowThreshold() {
        Set<String> a = PlaybookService.tokens("build me a spring boot rest api with auth");
        Set<String> b = PlaybookService.tokens("write a poem about the ocean at night");
        assertThat(PlaybookService.jaccard(a, b)).isLessThan(0.8);
    }

    @Test
    void shortTokensAreIgnored() {
        // "a", "to", "of" dropped (length <= 2) → only meaningful tokens remain.
        assertThat(PlaybookService.tokens("go to a zoo")).containsExactlyInAnyOrder("zoo");
    }
}
