package com.jarvis.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class TaskRecoveryTest {

    @Test
    void marksOrphanedRunningTasksAsFailed() {
        TaskRepository repo = mock(TaskRepository.class);
        Task orphan = new Task("task_1", "build something big");   // starts RUNNING
        when(repo.findByStatus(TaskStatus.RUNNING)).thenReturn(List.of(orphan));
        when(repo.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        int recovered = new TaskService(repo).recoverInterrupted();

        assertThat(recovered).isEqualTo(1);
        assertThat(orphan.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(orphan.getSummary()).contains("Interrupted by a restart");
    }

    @Test
    void noOrphansIsANoOp() {
        TaskRepository repo = mock(TaskRepository.class);
        when(repo.findByStatus(TaskStatus.RUNNING)).thenReturn(List.of());
        assertThat(new TaskService(repo).recoverInterrupted()).isZero();
    }
}
