package com.jarvis.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.approval.ApprovalService;
import com.jarvis.audit.AuditService;
import com.jarvis.brain.Orchestrator;
import com.jarvis.command.CommandEngine;
import com.jarvis.command.CommandResult;
import com.jarvis.connectors.ConnectorRegistry;

class WorkflowEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private WorkflowRepository workflows;
    private WorkflowRunRepository runs;
    private CommandEngine commandEngine;
    private ApprovalService approvals;
    private WorkflowEngine engine;
    private final Map<String, WorkflowRun> runStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        workflows = mock(WorkflowRepository.class);
        runs = mock(WorkflowRunRepository.class);
        commandEngine = mock(CommandEngine.class);
        approvals = mock(ApprovalService.class);
        engine = new WorkflowEngine(workflows, runs, mapper, commandEngine,
                mock(Orchestrator.class), mock(ConnectorRegistry.class), approvals, mock(AuditService.class),
                mock(com.jarvis.notification.NotificationService.class));

        when(runs.save(any(WorkflowRun.class))).thenAnswer(inv -> {
            WorkflowRun r = inv.getArgument(0);
            runStore.put(r.getId(), r);
            return r;
        });
        when(runs.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(runStore.get(inv.getArgument(0))));
    }

    private Workflow workflow(WorkflowStep... steps) {
        try {
            Workflow wf = new Workflow("wf1", "Test", "", TriggerType.MANUAL, null, true,
                    mapper.writeValueAsString(List.of(steps)));
            when(workflows.findById("wf1")).thenReturn(Optional.of(wf));
            return wf;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<StepResult> results(WorkflowRun run) throws Exception {
        return mapper.readValue(run.getResultsJson(), new TypeReference<List<StepResult>>() {});
    }

    @Test
    void runsCommandAndNotifyStepsToDone() throws Exception {
        when(commandEngine.execute("/status")).thenReturn(CommandResult.ok("status", "System status", null));
        Workflow wf = workflow(
                new WorkflowStep("s1", "Check status", StepType.COMMAND, Map.of("command", "/status"), 1),
                new WorkflowStep("s2", "Notify", StepType.NOTIFY, Map.of("message", "done"), 1));

        WorkflowRun run = engine.start(wf, "manual");

        assertThat(run.getStatus()).isEqualTo(RunStatus.DONE);
        List<StepResult> r = results(run);
        assertThat(r).extracting(StepResult::status).containsExactly(StepStatus.DONE, StepStatus.DONE);
        assertThat(r.get(0).output()).isEqualTo("System status");
        assertThat(r.get(1).output()).contains("done");
    }

    @Test
    void retriesThenFailsOnBadStep() {
        when(commandEngine.execute("/bad")).thenReturn(CommandResult.error("boom"));
        Workflow wf = workflow(new WorkflowStep("s1", "Bad", StepType.COMMAND, Map.of("command", "/bad"), 3));

        WorkflowRun run = engine.start(wf, "manual");

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void approvalGatePausesThenResumesToDone() throws Exception {
        Workflow wf = workflow(
                new WorkflowStep("s1", "Approve me", StepType.APPROVAL, Map.of("title", "Gate"), 1),
                new WorkflowStep("s2", "Notify", StepType.NOTIFY, Map.of("message", "after approval"), 1));

        WorkflowRun run = engine.start(wf, "manual");
        assertThat(run.getStatus()).isEqualTo(RunStatus.PAUSED);
        assertThat(results(run).get(0).status()).isEqualTo(StepStatus.AWAITING_APPROVAL);

        // Capture the deferred action the engine handed to the Approval Center and run it (= approve).
        ArgumentCaptor<Callable<Object>> action = ArgumentCaptor.forClass(Callable.class);
        verify(approvals).submit(anyString(), anyString(), anyString(), any(), anyString(), action.capture());
        action.getValue().call();

        WorkflowRun resumed = runStore.get(run.getId());
        assertThat(resumed.getStatus()).isEqualTo(RunStatus.DONE);
        List<StepResult> r = results(resumed);
        assertThat(r).extracting(StepResult::status).containsExactly(StepStatus.DONE, StepStatus.DONE);
    }
}
