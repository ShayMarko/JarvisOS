package com.jarvis.task;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** The Task Manager (spec §6) — tracks each Brain task and its history. */
@Service
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

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
}
