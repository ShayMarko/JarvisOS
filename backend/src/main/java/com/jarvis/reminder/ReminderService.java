package com.jarvis.reminder;

import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jarvis.brain.Orchestrator;
import com.jarvis.common.Ids;
import com.jarvis.notification.NotificationService;

import lombok.extern.slf4j.Slf4j;

/**
 * One-shot reminders & deferred tasks. {@link #schedule} stores an absolute fire time; a sweep fires
 * anything due — pushing a notification (which mirrors to Discord/Telegram) and, for a deferred task,
 * running the message through the Brain. Recurring jobs stay with the workflow/routine engine; this is
 * purely "do/notify once at time T".
 */
@Service
@Slf4j
public class ReminderService {

    private final ReminderRepository repository;
    private final NotificationService notifications;
    private final Orchestrator orchestrator;

    public ReminderService(ReminderRepository repository, NotificationService notifications,
                           @Lazy Orchestrator orchestrator) {   // @Lazy breaks the tool→service→brain cycle
        this.repository = repository;
        this.notifications = notifications;
        this.orchestrator = orchestrator;
    }

    @Transactional
    public Reminder schedule(String message, Instant fireAt, boolean runTask, String source) {
        return repository.save(new Reminder(Ids.generate("rem"), message, fireAt, runTask, source));
    }

    @Transactional(readOnly = true)
    public List<Reminder> pending() {
        return repository.findByFiredFalseOrderByFireAtAsc();
    }

    /** Fire anything due. Each reminder is marked fired before it runs, so a slow task never double-fires. */
    @Scheduled(fixedDelayString = "${jarvis.reminders.sweep-ms:30000}")
    public void sweep() {
        List<Reminder> due = claimDue();
        for (Reminder r : due) {
            try {
                if (r.isRunTask()) {
                    String answer = orchestrator.handle(r.getMessage(), "reminder").answer();
                    String body = r.getMessage() + (answer == null || answer.isBlank() ? "" : "\n\n" + trim(answer));
                    notifications.notify("success", "⏰ Scheduled task done", body, "reminder");
                } else {
                    notifications.notify("info", "⏰ Reminder", r.getMessage(), "reminder");
                }
            } catch (Exception e) {
                log.warn("Reminder {} failed to fire: {}", r.getId(), e.getMessage());
                notifications.notify("error", "⏰ Reminder failed", r.getMessage() + "\n(" + e.getMessage() + ")", "reminder");
            }
        }
    }

    /** Mark all currently-due reminders fired in one transaction and return them to act on. */
    @Transactional
    protected List<Reminder> claimDue() {
        List<Reminder> due = repository.findByFiredFalseAndFireAtLessThanEqualOrderByFireAtAsc(Instant.now());
        Instant now = Instant.now();
        for (Reminder r : due) {
            r.setFired(true);
            r.setFiredAt(now);
        }
        repository.saveAll(due);
        return due;
    }

    private static String trim(String s) {
        return s.length() > 1500 ? s.substring(0, 1500) + "…" : s;
    }
}
