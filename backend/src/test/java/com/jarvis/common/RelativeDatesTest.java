package com.jarvis.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class RelativeDatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 14);

    @Test
    void stampsRelativePhrases() {
        assertThat(RelativeDates.anchor("Started work on Jarvis about a month ago", TODAY))
                .isEqualTo("Started work on Jarvis about a month ago (as of 2026-06-14)");
        assertThat(RelativeDates.anchor("Met the client yesterday", TODAY)).contains("(as of 2026-06-14)");
        assertThat(RelativeDates.anchor("Shipping next week", TODAY)).contains("(as of 2026-06-14)");
        assertThat(RelativeDates.anchor("Refactored it 3 weeks ago", TODAY)).contains("(as of 2026-06-14)");
    }

    @Test
    void leavesAbsoluteOrNeutralTextAlone() {
        // already has a year → untouched
        assertThat(RelativeDates.anchor("Started in May 2026", TODAY)).isEqualTo("Started in May 2026");
        // no relative reference → untouched
        assertThat(RelativeDates.anchor("Prefers concise answers", TODAY)).isEqualTo("Prefers concise answers");
        // already anchored → not double-stamped
        assertThat(RelativeDates.anchor("Started a month ago (as of 2026-06-14)", TODAY))
                .isEqualTo("Started a month ago (as of 2026-06-14)");
        // null / blank are safe
        assertThat(RelativeDates.anchor(null, TODAY)).isNull();
        assertThat(RelativeDates.anchor("", TODAY)).isEqualTo("");
    }
}
