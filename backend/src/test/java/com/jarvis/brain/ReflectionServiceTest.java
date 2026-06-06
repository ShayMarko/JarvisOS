package com.jarvis.brain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReflectionServiceTest {

    @Test
    void parsesLessonLines() {
        var out = ReflectionService.parseLessons(
                "Prefers concise answers :: Shay likes short replies.\n"
                + "- Build under Projects :: Always put apps in Projects/.\nNONE");
        assertThat(out).hasSize(2);
        assertThat(out.get(0)[0]).isEqualTo("Prefers concise answers");
        assertThat(out.get(0)[1]).contains("short replies");
        assertThat(out.get(1)[0]).isEqualTo("Build under Projects");
    }

    @Test
    void noneOrGarbageYieldsEmpty() {
        assertThat(ReflectionService.parseLessons("NONE")).isEmpty();
        assertThat(ReflectionService.parseLessons("just prose without any separator")).isEmpty();
        assertThat(ReflectionService.parseLessons(null)).isEmpty();
    }
}
