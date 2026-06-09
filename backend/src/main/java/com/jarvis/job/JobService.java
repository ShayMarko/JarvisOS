package com.jarvis.job;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.jarvis.audit.AuditService;
import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.common.Ids;
import com.jarvis.notification.NotificationService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * The background job queue — runs long Brain requests detached on a bounded worker pool, persists each as a
 * durable {@link Job}, pushes a notification (Discord/bell) on completion, and supports cancellation. This is
 * what lets Jarvis say "on it — I'll ping you when it's done" instead of blocking a voice/phone caller.
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final int MAX_RESULT = 8000;
    private static final int MAX_ERROR = 4000;

    /** Bail-out thrown from the step callback when a job has been cancelled. */
    static final class JobCancelledException extends RuntimeException {
        JobCancelledException() {
            super("cancelled");
        }
    }

    private final JobRepository repository;
    // ObjectProvider → resolved lazily at run time, so the Brain can hold a run_in_background tool without
    // an eager ToolRegistry → JobService → Orchestrator → ToolRegistry bean cycle.
    private final org.springframework.beans.factory.ObjectProvider<Orchestrator> orchestratorProvider;
    private final NotificationService notifications;
    private final AuditService audit;

    // Bounded pool — leaves headroom on the 16GB box (don't starve the foreground chat / Ollama).
    private final ExecutorService exec =
            Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final Map<String, Future<?>> running = new ConcurrentHashMap<>();

    /** On startup, any job left QUEUED/RUNNING by a previous JVM can't still be alive → mark it FAILED. */
    @PostConstruct
    void recoverInterrupted() {
        int n = 0;
        for (JobStatus s : List.of(JobStatus.RUNNING, JobStatus.QUEUED)) {
            for (Job j : repository.findByStatus(s)) {
                j.setStatus(JobStatus.FAILED);
                j.setError("Interrupted by a restart.");
                j.setFinishedAt(Instant.now());
                repository.save(j);
                n++;
            }
        }
        if (n > 0) {
            log.info("Recovered {} interrupted background job(s) → FAILED.", n);
        }
    }

    /** Queue a request to run in the background. Returns the persisted job immediately (status QUEUED). */
    public Job submit(String request, String sessionId, String source) {
        Job job = repository.save(new Job(Ids.generate("job"), request,
                sessionId == null || sessionId.isBlank() ? "background" : sessionId,
                source == null ? "api" : source));
        audit.record("JOB", "job:submit", request, "QUEUED", "id=" + job.getId() + "; source=" + job.getSource());
        Future<?> f = exec.submit(() -> run(job.getId()));
        running.put(job.getId(), f);
        return job;
    }

    private void run(String jobId) {
        Job job = repository.findById(jobId).orElse(null);
        if (job == null || cancelled.contains(jobId)) {
            finalizeCancelled(jobId);
            return;
        }
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        repository.save(job);
        try {
            ChatResponse resp = orchestratorProvider.getObject().handle(job.getRequest(), job.getSessionId(), step -> {
                if (cancelled.contains(jobId)) {
                    throw new JobCancelledException();
                }
            });
            String answer = resp == null || resp.answer() == null ? "" : resp.answer();
            job.setAgent(resp == null ? null : resp.agent());
            job.setModel(resp == null ? null : resp.model());
            job.setTokens(resp == null ? 0 : resp.tokens());
            job.setResult(trim(answer, MAX_RESULT));
            job.setStatus(JobStatus.DONE);
            job.setFinishedAt(Instant.now());
            repository.save(job);
            audit.record("JOB", "job:done", job.getRequest(), "DONE",
                    "id=" + jobId + "; agent=" + job.getAgent() + "; tokens=" + job.getTokens());
            notifications.notify("success", "Background job finished",
                    preview(job.getRequest()) + " → " + preview(answer), "job");
        } catch (JobCancelledException ce) {
            finalizeCancelled(jobId);
        } catch (Exception e) {
            // If cancellation was requested, ANY resulting exception (incl. an interrupt) means CANCELLED.
            if (cancelled.contains(jobId) || e instanceof InterruptedException
                    || e.getCause() instanceof InterruptedException) {
                finalizeCancelled(jobId);
                return;
            }
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            job.setStatus(JobStatus.FAILED);
            job.setError(trim(reason, MAX_ERROR));
            job.setFinishedAt(Instant.now());
            repository.save(job);
            audit.record("JOB", "job:failed", job.getRequest(), "FAILED", "id=" + jobId + "; " + reason);
            notifications.notify("error", "Background job failed", preview(job.getRequest()) + " — " + reason, "job");
            log.warn("Background job {} failed: {}", jobId, reason);
        } finally {
            cancelled.remove(jobId);
            running.remove(jobId);
        }
    }

    /** Request cancellation. A running job bails at its next step; a queued job is dropped immediately. */
    public Job cancel(String id) {
        Job job = repository.findById(id).orElse(null);
        if (job == null) {
            return null;
        }
        if (job.getStatus() == JobStatus.DONE || job.getStatus() == JobStatus.FAILED
                || job.getStatus() == JobStatus.CANCELLED) {
            return job;   // already terminal
        }
        cancelled.add(id);
        Future<?> f = running.get(id);
        if (f != null) {
            f.cancel(true);   // best-effort interrupt; the step-callback guard is the clean stop
        }
        finalizeCancelled(id);
        audit.record("JOB", "job:cancel", job.getRequest(), "CANCELLED", "id=" + id);
        return repository.findById(id).orElse(job);
    }

    private void finalizeCancelled(String id) {
        Job job = repository.findById(id).orElse(null);
        if (job != null && job.getStatus() != JobStatus.DONE && job.getStatus() != JobStatus.FAILED) {
            job.setStatus(JobStatus.CANCELLED);
            if (job.getFinishedAt() == null) {
                job.setFinishedAt(Instant.now());
            }
            repository.save(job);
        }
    }

    public Job get(String id) {
        return repository.findById(id).orElse(null);
    }

    public List<Job> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, limit)));
    }

    public long activeCount() {
        return repository.countByStatus(JobStatus.RUNNING) + repository.countByStatus(JobStatus.QUEUED);
    }

    @PreDestroy
    void shutdown() {
        exec.shutdownNow();
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String preview(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() <= 120 ? one : one.substring(0, 120) + "…";
    }
}
