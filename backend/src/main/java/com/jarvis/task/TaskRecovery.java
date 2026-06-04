package com.jarvis.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** On startup, reconcile tasks orphaned by a previous crash/restart (RUNNING → FAILED). */
@Component
@RequiredArgsConstructor
public class TaskRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRecovery.class);

    private final TaskService tasks;

    @Override
    public void run(ApplicationArguments args) {
        int recovered = tasks.recoverInterrupted();
        if (recovered > 0) {
            log.info("Durable tasks: reconciled {} task(s) left running by a previous restart → FAILED.", recovered);
        }
    }
}
