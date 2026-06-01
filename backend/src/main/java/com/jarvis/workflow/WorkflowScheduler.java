package com.jarvis.workflow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Real cron scheduler for SCHEDULE-triggered workflows (spec §12 Recurring
 * Schedules). Uses a {@link ThreadPoolTaskScheduler} with a {@link CronTrigger}
 * per workflow (Spring 6-field cron: {@code sec min hour dom mon dow}).
 */
@Component
public class WorkflowScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);

    private final WorkflowRepository workflows;
    private final WorkflowEngine engine;
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public WorkflowScheduler(WorkflowRepository workflows, WorkflowEngine engine) {
        this.workflows = workflows;
        this.engine = engine;
    }

    @PostConstruct
    void init() {
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("jarvis-cron-");
        taskScheduler.initialize();
        workflows.findByTriggerTypeAndEnabledTrue(TriggerType.SCHEDULE).forEach(this::schedule);
    }

    /** (Re)schedule a workflow; cancels any existing schedule first. */
    public synchronized void schedule(Workflow wf) {
        unschedule(wf.getId());
        if (!wf.isEnabled() || wf.getTriggerType() != TriggerType.SCHEDULE
                || wf.getCron() == null || wf.getCron().isBlank()) {
            return;
        }
        String id = wf.getId();
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> workflows.findById(id).ifPresent(w -> {
                    try {
                        engine.start(w, "schedule");
                    } catch (Exception e) {
                        log.error("Scheduled workflow {} failed", id, e);
                    }
                }),
                new CronTrigger(wf.getCron()));
        if (future != null) {
            futures.put(id, future);
            log.info("Scheduled workflow {} with cron '{}'", wf.getName(), wf.getCron());
        }
    }

    public synchronized void unschedule(String workflowId) {
        ScheduledFuture<?> existing = futures.remove(workflowId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    public boolean isScheduled(String workflowId) {
        return futures.containsKey(workflowId);
    }
}
