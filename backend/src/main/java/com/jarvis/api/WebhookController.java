package com.jarvis.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.error.Exceptions.ConflictException;
import com.jarvis.workflow.RunView;
import com.jarvis.workflow.TriggerType;
import com.jarvis.workflow.WorkflowService;
import com.jarvis.workflow.WorkflowView;

import lombok.RequiredArgsConstructor;

/**
 * Inbound webhook receiver (spec §12 triggers). An external service POSTs to
 * {@code /api/webhooks/{workflowId}} to fire a WEBHOOK-triggered workflow. Only
 * workflows whose trigger is WEBHOOK and that are enabled can be fired this way.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WorkflowService workflows;

    @PostMapping("/{workflowId}")
    public RunView fire(@PathVariable String workflowId, @RequestBody(required = false) String payload) {
        WorkflowView wf = workflows.get(workflowId); // throws NotFound (404) if absent
        if (wf.triggerType() != TriggerType.WEBHOOK) {
            throw new ConflictException("Workflow '" + wf.name() + "' is not a WEBHOOK-triggered workflow.");
        }
        if (!wf.enabled()) {
            throw new ConflictException("Workflow '" + wf.name() + "' is disabled.");
        }
        return workflows.run(workflowId, "webhook");
    }
}
