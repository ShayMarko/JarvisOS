package com.jarvis.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.workflow.RunView;
import com.jarvis.workflow.WorkflowDraft;
import com.jarvis.workflow.WorkflowService;
import com.jarvis.workflow.WorkflowView;

/** Workflow Builder + runs endpoints (spec §12). */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflows;

    public WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping
    public List<WorkflowView> list() {
        return workflows.list();
    }

    @GetMapping("/runs")
    public List<RunView> recentRuns(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return workflows.recentRuns(Math.min(limit, 500));
    }

    @GetMapping("/{id}")
    public WorkflowView get(@PathVariable String id) {
        return workflows.get(id);
    }

    @GetMapping("/{id}/runs")
    public List<RunView> runsFor(@PathVariable String id) {
        return workflows.runsFor(id);
    }

    @PostMapping
    public ResponseEntity<WorkflowView> create(@RequestBody WorkflowDraft draft) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflows.create(draft));
    }

    @PutMapping("/{id}")
    public WorkflowView update(@PathVariable String id, @RequestBody WorkflowDraft draft) {
        return workflows.update(id, draft);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        workflows.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Manual / webhook trigger. */
    @PostMapping("/{id}/run")
    public RunView run(@PathVariable String id,
                       @RequestParam(name = "trigger", defaultValue = "manual") String trigger) {
        return workflows.run(id, trigger);
    }
}
