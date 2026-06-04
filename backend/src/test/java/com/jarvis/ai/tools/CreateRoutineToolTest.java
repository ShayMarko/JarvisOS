package com.jarvis.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The plain-English → Spring-cron mapping for routines. */
class CreateRoutineToolTest {

    @Test
    void mapsCommonPhrasesToCron() {
        assertThat(CreateRoutineTool.toCron("every morning")).isEqualTo("0 0 8 * * *");
        assertThat(CreateRoutineTool.toCron("every evening")).isEqualTo("0 0 18 * * *");
        assertThat(CreateRoutineTool.toCron("hourly")).isEqualTo("0 0 * * * *");
        assertThat(CreateRoutineTool.toCron("every 15 minutes")).isEqualTo("0 */15 * * * *");
        assertThat(CreateRoutineTool.toCron("every 2 hours")).isEqualTo("0 0 */2 * * *");
    }

    @Test
    void parsesExplicitTimes() {
        assertThat(CreateRoutineTool.toCron("daily at 18:30")).isEqualTo("0 30 18 * * *");
        assertThat(CreateRoutineTool.toCron("every day at 6pm")).isEqualTo("0 0 18 * * *");
        assertThat(CreateRoutineTool.toCron("at 9")).isEqualTo("0 0 9 * * *");
    }

    @Test
    void returnsNullForUnparseablePhrases() {
        assertThat(CreateRoutineTool.toCron("sometime soon")).isNull();
        assertThat(CreateRoutineTool.toCron("")).isNull();
        assertThat(CreateRoutineTool.toCron(null)).isNull();
    }
}
