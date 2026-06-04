package com.jarvis.task;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** The Task Manager (spec §6) — tracks each Brain task and its history. */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository repository;


    public Task start(String request) {
        return repository.save(new Task(
                "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), request));
    }

    public void finish(Task task, String agent, String summary) {
        task.setAgent(agent);
        task.setSummary(summary);
        task.setStatus(TaskStatus.DONE);
        task.setFinishedAt(Instant.now());
        repository.save(task);
    }

    public void fail(Task task, String error) {
        task.setStatus(TaskStatus.FAILED);
        task.setSummary(error);
        task.setFinishedAt(Instant.now());
        repository.save(task);
    }

    public List<Task> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    public long activeCount() {
        return repository.countByStatus(TaskStatus.RUNNING);
    }

    /**
     * Durable-task reconciliation: tasks left RUNNING from a previous run can't still be executing (their
     * in-memory work died with the JVM), so mark them FAILED on startup instead of dangling forever.
     * Returns how many were reconciled.
     */
    public int recoverInterrupted() {
        List<Task> orphans = repository.findByStatus(TaskStatus.RUNNING);
        for (Task t : orphans) {
            t.setStatus(TaskStatus.FAILED);
            t.setSummary("Interrupted by a restart.");
            t.setFinishedAt(Instant.now());
            repository.save(t);
        }
        return orphans.size();
    }
}
