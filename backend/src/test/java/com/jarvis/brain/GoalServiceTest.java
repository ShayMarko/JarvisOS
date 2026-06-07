package com.jarvis.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryDraft;
import com.jarvis.memory.MemoryService;

class GoalServiceTest {

    private MemoryService memory;
    private GoalService goals;

    @BeforeEach
    void setUp() {
        memory = mock(MemoryService.class);
        goals = new GoalService(memory);
    }

    @Test
    void currentIsNullWhenNoGoalSet() {
        when(memory.list("")).thenReturn(List.of());
        assertThat(goals.current()).isNull();
    }

    @Test
    void currentReadsTheNorthStarMemory() {
        Memory g = goal("Reach $500/mo by Q4");
        when(memory.list("")).thenReturn(List.of(g));
        assertThat(goals.current()).isEqualTo("Reach $500/mo by Q4");
    }

    @Test
    void setCreatesWhenNoneExists() {
        when(memory.list("")).thenReturn(List.of());
        goals.set("Ship 3 products");
        verify(memory).create(any(MemoryDraft.class));
        verify(memory, never()).update(any(), any());
    }

    @Test
    void setUpdatesExistingGoalInPlace() {
        Memory existing = goal("old goal");
        when(existing.getId()).thenReturn("mem_goal");
        when(memory.list("")).thenReturn(List.of(existing));
        goals.set("new goal");
        verify(memory).update(eq("mem_goal"), any(MemoryDraft.class));
        verify(memory, never()).create(any());
    }

    private Memory goal(String content) {
        Memory m = mock(Memory.class);
        when(m.getCategory()).thenReturn(GoalService.CATEGORY);
        when(m.getTitle()).thenReturn(GoalService.TITLE);
        when(m.getContent()).thenReturn(content);
        return m;
    }
}
