package com.jarvis.telegram;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jarvis.command.CommandEngine;
import com.jarvis.command.CommandResult;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * The inbound half of the Telegram bridge: long-polls for messages and runs each through the SAME
 * cognitive door as the HUD ({@link CommandEngine}) — so anything you can type in Jarvis you can text
 * from your phone, with no special path. Each chat is its own session (continuity per phone thread).
 * Runs on a single daemon thread, started only when a bot token is configured.
 */
@Component
@RequiredArgsConstructor
public class TelegramBridge {

    private static final Logger log = LoggerFactory.getLogger(TelegramBridge.class);

    private final TelegramService telegram;
    private final CommandEngine commands;

    private volatile boolean running = false;
    private Thread thread;

    @PostConstruct
    void start() {
        if (!telegram.pollingEnabled()) {
            return;   // dormant on a default install (no token / polling off)
        }
        running = true;
        thread = new Thread(this::loop, "telegram-bridge");
        thread.setDaemon(true);
        thread.start();
        log.info("Telegram bridge started (inbound polling on).");
    }

    @PreDestroy
    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        long offset = 0;
        while (running) {
            try {
                List<TelegramService.Update> updates = telegram.poll(offset);
                for (TelegramService.Update u : updates) {
                    offset = Math.max(offset, u.updateId() + 1);   // ack so we don't re-read it
                    handle(u);
                }
                if (updates.isEmpty()) {
                    Thread.sleep(telegram.idlePollMs());   // brief breather between empty long-polls
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.debug("Telegram bridge loop error (continuing): {}", e.getMessage());
                try { Thread.sleep(telegram.errorBackoffMs()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private void handle(TelegramService.Update u) {
        try {
            CommandResult result = commands.execute(u.text(), "telegram:" + u.chatId());
            String reply = result.message() != null && !result.message().isBlank()
                    ? result.message() : "Done.";
            if (result.status() == CommandResult.Status.ERROR) {
                reply = "⚠️ " + reply;
            }
            telegram.send(u.chatId(), reply);
        } catch (RuntimeException e) {
            log.warn("Telegram message handling failed: {}", e.getMessage());
            telegram.send(u.chatId(), "⚠️ Something went wrong handling that.");
        }
    }
}
