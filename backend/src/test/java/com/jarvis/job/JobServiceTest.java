package com.jarvis.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.jarvis.agent.Step;
import com.jarvis.audit.AuditService;
import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.notification.NotificationService;

/**
 * Tests the background job lifecycle with a mocked Orchestrator + an in-memory repository: a job runs
 * async to DONE, a thrown error → FAILED, and cancellation bails a running job → CANCELLED.
 */
class JobServiceTest {

    private final ConcurrentHashMap<String, Job> store = new ConcurrentHashMap<>();
    private JobRepository repo;
    private Orchestrator orch;
    private JobService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        repo = mock(JobRepository.class);
        lenient().when(repo.save(any(Job.class))).thenAnswer(inv -> {
            Job j = inv.getArgument(0);
            store.put(j.getId(), j);
            return j;
        });
        lenient().when(repo.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        lenient().when(repo.findByStatus(any())).thenAnswer(inv -> {
            List<Job> out = new ArrayList<>();
            for (Job j : store.values()) {
                if (j.getStatus() == inv.getArgument(0)) {
                    out.add(j);
                }
            }
            return out;
        });

        orch = mock(Orchestrator.class);
        ObjectProvider<Orchestrator> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(orch);

        service = new JobService(repo, provider, mock(NotificationService.class), mock(AuditService.class));
    }

    @Test
    void jobRunsToDone() throws Exception {
        when(orch.handle(any(), any(), any()))
                .thenReturn(new ChatResponse("the answer", "general", List.of(), "task_1", 42, "ollama"));

        Job job = service.submit("do a thing", "s1", "test");
        Job done = awaitTerminal(job.getId());

        assertThat(done.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(done.getResult()).isEqualTo("the answer");
        assertThat(done.getAgent()).isEqualTo("general");
        assertThat(done.getTokens()).isEqualTo(42);
    }

    @Test
    void jobFailureIsRecorded() throws Exception {
        when(orch.handle(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        Job job = service.submit("break it", "s1", "test");
        Job done = awaitTerminal(job.getId());

        assertThat(done.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(done.getError()).contains("boom");
    }

    @Test
    void runningJobCanBeCancelled() throws Exception {
        // Orchestrator that loops emitting steps until the job's step-callback throws (on cancel).
        when(orch.handle(any(), any(), any())).thenAnswer(inv -> {
            Consumer<Step> onStep = inv.getArgument(2);
            while (true) {
                onStep.accept(new Step("tool", "working", "..."));   // throws JobCancelledException on cancel
                Thread.sleep(10);
            }
        });

        Job job = service.submit("long task", "s1", "test");
        awaitStatus(job.getId(), JobStatus.RUNNING);
        service.cancel(job.getId());
        Job done = awaitTerminal(job.getId());

        assertThat(done.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void cancellingAFinishedJobIsNoOp() throws Exception {
        when(orch.handle(any(), any(), any()))
                .thenReturn(new ChatResponse("done", "general", List.of(), "t", 1, "ollama"));
        Job job = service.submit("quick", "s1", "test");
        awaitTerminal(job.getId());

        Job after = service.cancel(job.getId());
        assertThat(after.getStatus()).isEqualTo(JobStatus.DONE);   // unchanged
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private Job awaitTerminal(String id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Job j = store.get(id);
            if (j != null && (j.getStatus() == JobStatus.DONE || j.getStatus() == JobStatus.FAILED
                    || j.getStatus() == JobStatus.CANCELLED)) {
                return j;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("job " + id + " did not reach a terminal state in time");
    }

    private void awaitStatus(String id, JobStatus status) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Job j = store.get(id);
            if (j != null && j.getStatus() == status) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("job " + id + " did not reach " + status + " in time");
    }
}
