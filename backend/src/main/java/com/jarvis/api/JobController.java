package com.jarvis.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.job.Job;
import com.jarvis.job.JobService;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** Background jobs: submit a long task, list/inspect them, cancel one. The detached/headless work loop. */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobs;

    public record JobRequest(@NotBlank String input, String sessionId) {}

    @PostMapping
    public Job submit(@jakarta.validation.Valid @RequestBody JobRequest req) {
        return jobs.submit(req.input(), req.sessionId(), "api");
    }

    @GetMapping
    public List<Job> list(@RequestParam(defaultValue = "30") int limit) {
        return jobs.recent(limit);
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable String id) {
        Job j = jobs.get(id);
        return j == null ? Map.of("error", "no such job: " + id) : j;
    }

    @PostMapping("/{id}/cancel")
    public Object cancel(@PathVariable String id) {
        Job j = jobs.cancel(id);
        return j == null ? Map.of("error", "no such job: " + id) : j;
    }
}
