package com.jarvis.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

/** Workflow CRUD + run/history, with cron (re)scheduling on changes (spec §12). */
@Service
public class WorkflowService {

    private final WorkflowRepository workflows;
    private final WorkflowRunRepository runs;
    private final WorkflowEngine engine;
    private final WorkflowScheduler scheduler;
    private final ObjectMapper mapper;

    public WorkflowService(WorkflowRepository workflows, WorkflowRunRepository runs, WorkflowEngine engine,
                           WorkflowScheduler scheduler, ObjectMapper mapper) {
        this.workflows = workflows;
        this.runs = runs;
        this.engine = engine;
        this.scheduler = scheduler;
        this.mapper = mapper;
    }

    public List<WorkflowView> list() {
        return workflows.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    public WorkflowView get(String id) {
        return toView(require(id));
    }

    public WorkflowView create(WorkflowDraft draft) {
        Workflow wf = new Workflow(
                "wf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                draft.name(), draft.description(),
                draft.triggerType() != null ? draft.triggerType() : TriggerType.MANUAL,
                draft.cron(), draft.enabled() == null || draft.enabled(),
                writeSteps(draft.steps()));
        Workflow saved = workflows.save(wf);
        scheduler.schedule(saved);
        return toView(saved);
    }

    public WorkflowView update(String id, WorkflowDraft draft) {
        Workflow wf = require(id);
        if (draft.name() != null) wf.setName(draft.name());
        if (draft.description() != null) wf.setDescription(draft.description());
        if (draft.triggerType() != null) wf.setTriggerType(draft.triggerType());
        if (draft.cron() != null) wf.setCron(draft.cron());
        if (draft.enabled() != null) wf.setEnabled(draft.enabled());
        if (draft.steps() != null) wf.setStepsJson(writeSteps(draft.steps()));
        wf.setUpdatedAt(Instant.now());
        Workflow saved = workflows.save(wf);
        scheduler.schedule(saved); // reschedule with new cron/enabled state
        return toView(saved);
    }

    public void delete(String id) {
        require(id);
        scheduler.unschedule(id);
        workflows.deleteById(id);
    }

    public RunView run(String id, String trigger) {
        Workflow wf = require(id);
        return toRunView(engine.start(wf, trigger));
    }

    public List<RunView> recentRuns(int limit) {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit)).stream().map(this::toRunView).toList();
    }

    public List<RunView> runsFor(String workflowId) {
        return runs.findByWorkflowIdOrderByStartedAtDesc(workflowId).stream().map(this::toRunView).toList();
    }

    private Workflow require(String id) {
        return workflows.findById(id).orElseThrow(() -> new NotFoundException("No workflow " + id));
    }

    private WorkflowView toView(Workflow wf) {
        return new WorkflowView(wf.getId(), wf.getName(), wf.getDescription(), wf.getTriggerType(),
                wf.getCron(), wf.isEnabled(), scheduler.isScheduled(wf.getId()),
                readSteps(wf.getStepsJson()), wf.getCreatedAt());
    }

    private RunView toRunView(WorkflowRun run) {
        return new RunView(run.getId(), run.getWorkflowId(), run.getStatus(), run.getCurrentStep(),
                run.getTrigger(), run.getStartedAt(), run.getFinishedAt(), readResults(run.getResultsJson()));
    }

    private String writeSteps(List<WorkflowStep> steps) {
        try {
            return mapper.writeValueAsString(steps == null ? List.of() : steps);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid steps: " + e.getMessage(), e);
        }
    }

    private List<WorkflowStep> readSteps(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<WorkflowStep>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<StepResult> readResults(String json) {
        try {
            return json == null ? List.of() : mapper.readValue(json, new TypeReference<List<StepResult>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
