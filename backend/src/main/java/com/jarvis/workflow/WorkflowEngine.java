package com.jarvis.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.approval.ApprovalService;
import com.jarvis.audit.AuditService;
import com.jarvis.brain.Orchestrator;
import com.jarvis.command.CommandEngine;
import com.jarvis.command.CommandResult;
import com.jarvis.connectors.ConnectorRegistry;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.security.RiskLevel;

/**
 * The durable workflow engine (spec §12, §12.1). Executes steps sequentially,
 * persisting state after each one. APPROVAL steps pause the run and resume it
 * when the Approval Center clears them; failed steps retry up to the step's
 * budget before the run fails. Each step calls a real Jarvis capability.
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowRepository workflows;
    private final WorkflowRunRepository runs;
    private final ObjectMapper mapper;
    private final CommandEngine commandEngine;
    private final Orchestrator orchestrator;
    private final ConnectorRegistry connectors;
    private final ApprovalService approvals;
    private final AuditService audit;
    private final com.jarvis.notification.NotificationService notifications;

    public WorkflowEngine(WorkflowRepository workflows, WorkflowRunRepository runs, ObjectMapper mapper,
                          @Lazy CommandEngine commandEngine, Orchestrator orchestrator, ConnectorRegistry connectors,
                          ApprovalService approvals, AuditService audit,
                          com.jarvis.notification.NotificationService notifications) {
        this.workflows = workflows;
        this.runs = runs;
        this.mapper = mapper;
        this.commandEngine = commandEngine;
        this.orchestrator = orchestrator;
        this.connectors = connectors;
        this.approvals = approvals;
        this.audit = audit;
        this.notifications = notifications;
    }

    public WorkflowRun start(Workflow wf, String trigger) {
        List<WorkflowStep> steps = parseSteps(wf);
        List<StepResult> results = new ArrayList<>();
        steps.forEach(s -> results.add(StepResult.pending(s)));

        WorkflowRun run = new WorkflowRun("run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                wf.getId(), trigger, writeJson(results));
        runs.save(run);
        audit.record("WORKFLOW", "run:start", wf.getName(), "OK", "run=" + run.getId() + "; trigger=" + trigger);
        executeFrom(run, wf, steps, results, 0);
        return runs.findById(run.getId()).orElse(run);
    }

    /** Resume a paused run after its approval gate was approved. */
    public void resume(String runId) {
        WorkflowRun run = runs.findById(runId)
                .orElseThrow(() -> new NotFoundException("No run " + runId));
        Workflow wf = workflows.findById(run.getWorkflowId())
                .orElseThrow(() -> new NotFoundException("No workflow " + run.getWorkflowId()));
        List<WorkflowStep> steps = parseSteps(wf);
        List<StepResult> results = parseResults(run);

        int gate = run.getCurrentStep();
        results.set(gate, results.get(gate).as(StepStatus.DONE, "approved", 1));
        run.setStatus(RunStatus.RUNNING);
        executeFrom(run, wf, steps, results, gate + 1);
    }

    private void executeFrom(WorkflowRun run, Workflow wf, List<WorkflowStep> steps,
                             List<StepResult> results, int startIndex) {
        for (int i = startIndex; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);

            if (step.type() == StepType.APPROVAL) {
                results.set(i, results.get(i).as(StepStatus.AWAITING_APPROVAL, "waiting for approval", 0));
                save(run, RunStatus.PAUSED, i, results, false);
                String title = step.config() != null && step.config().containsKey("title")
                        ? step.configString("title") : "Workflow approval: " + wf.getName();
                approvals.submit("workflow:" + wf.getId(), title,
                        step.configString("why").isBlank() ? "Workflow step requires approval." : step.configString("why"),
                        RiskLevel.MEDIUM, wf.getName() + " → " + step.name(),
                        () -> { resume(run.getId()); return "workflow resumed"; });
                return; // paused; resume() continues when approved
            }

            StepResult result = runStep(step);
            results.set(i, result);
            save(run, RunStatus.RUNNING, i, results, false);
            if (result.status() == StepStatus.FAILED) {
                save(run, RunStatus.FAILED, i, results, true);
                audit.record("WORKFLOW", "run:failed", wf.getName(), "ERROR", "run=" + run.getId() + "; step=" + step.name());
                notifications.notify("error", "Workflow failed", wf.getName() + " — step \"" + step.name() + "\"", "workflow");
                return;
            }
        }
        save(run, RunStatus.DONE, steps.size(), results, true);
        audit.record("WORKFLOW", "run:done", wf.getName(), "OK", "run=" + run.getId());
        notifications.notify("success", "Workflow completed", wf.getName(), "workflow");
    }

    private StepResult runStep(WorkflowStep step) {
        int max = step.attemptsOrDefault();
        String lastError = "";
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                String output = execute(step);
                return new StepResult(step.id(), step.name(), step.type(), StepStatus.DONE, output, attempt);
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("Workflow step '{}' attempt {}/{} failed: {}", step.name(), attempt, max, lastError);
            }
        }
        return new StepResult(step.id(), step.name(), step.type(), StepStatus.FAILED, "error: " + lastError, max);
    }

    private String execute(WorkflowStep step) throws Exception {
        return switch (step.type()) {
            case COMMAND -> {
                CommandResult r = commandEngine.execute(step.configString("command"));
                if (r.status() == CommandResult.Status.ERROR) {
                    throw new IllegalStateException(r.message());
                }
                yield r.message();
            }
            case BRAIN -> orchestrator.handle(step.configString("prompt")).answer();
            case CONNECTOR -> {
                Object args = step.config() == null ? null : step.config().get("args");
                String argsJson = args == null ? "{}" : mapper.writeValueAsString(args);
                yield connectors.invoke(step.configString("connector"), step.configString("action"), argsJson);
            }
            case NOTIFY -> {
                String msg = step.configString("message");
                audit.record("WORKFLOW", "notify", msg, "OK", null);
                yield "🔔 " + msg;
            }
            case APPROVAL -> throw new IllegalStateException("APPROVAL handled by the engine, not execute()");
        };
    }

    private void save(WorkflowRun run, RunStatus status, int currentStep, List<StepResult> results, boolean terminal) {
        run.setStatus(status);
        run.setCurrentStep(currentStep);
        run.setResultsJson(writeJson(results));
        if (terminal) {
            run.setFinishedAt(Instant.now());
        }
        runs.save(run);
    }

    // --- JSON helpers ---------------------------------------------------------

    private List<WorkflowStep> parseSteps(Workflow wf) {
        try {
            return mapper.readValue(wf.getStepsJson(), new TypeReference<List<WorkflowStep>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Bad workflow steps JSON: " + e.getMessage(), e);
        }
    }

    private List<StepResult> parseResults(WorkflowRun run) {
        try {
            return new ArrayList<>(mapper.readValue(run.getResultsJson(), new TypeReference<List<StepResult>>() {}));
        } catch (Exception e) {
            throw new IllegalStateException("Bad run results JSON: " + e.getMessage(), e);
        }
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise workflow JSON", e);
        }
    }
}
