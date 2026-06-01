package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.task.Task;
import com.jarvis.task.TaskService;

/** Task history (spec §6, §10). */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService tasks;


    @GetMapping
    public List<Task> recent(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return tasks.recent(Math.min(limit, 500));
    }
}
