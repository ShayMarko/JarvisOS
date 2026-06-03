package com.jarvis.undo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The undo journal — a bounded LIFO stack of reversible actions that powers conversational "undo that".
 * Mutating capabilities (file write/create/delete) push an inverse "recipe" here when they act; the
 * {@code undo_last} tool pops the most recent one and runs it.
 *
 * <p>Recipes are plain {@link Callable}s captured by the producer, so the journal stays decoupled — it
 * knows nothing about files or any other subsystem, it just remembers how to reverse the last thing.
 * While a reversal runs, recording is suppressed so undoing an action doesn't itself become undoable.
 */
@Component
public class UndoJournal {

    private static final Logger log = LoggerFactory.getLogger(UndoJournal.class);
    private static final int MAX_ENTRIES = 50;

    /** A reversible action: what happened, when, and how to undo it. */
    public record Entry(String description, Instant at, Callable<String> reverse) {}

    private final Deque<Entry> stack = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean replaying = new AtomicBoolean(false);

    /** Remember how to reverse the action just performed. No-op while a reversal is in progress. */
    public void record(String description, Callable<String> reverse) {
        if (replaying.get() || description == null || reverse == null) {
            return;
        }
        stack.push(new Entry(description, Instant.now(), reverse));
        while (stack.size() > MAX_ENTRIES) {
            stack.removeLast();   // drop the oldest
        }
    }

    /** Reverse the most recent action and return a human summary. */
    public String undoLast() {
        Entry e = stack.poll();
        if (e == null) {
            return "There's nothing to undo.";
        }
        replaying.set(true);
        try {
            String detail = e.reverse().call();
            log.info("Undid: {}", e.description());
            return "Undid: " + e.description() + (detail == null || detail.isBlank() ? "" : " — " + detail);
        } catch (Exception ex) {
            // Reversal failed — push it back so the user can see/retry it.
            stack.push(e);
            return "Couldn't undo '" + e.description() + "': " + ex.getMessage();
        } finally {
            replaying.set(false);
        }
    }

    /** The most recent reversible actions, newest first (for "what can I undo?"). */
    public List<String> recent(int max) {
        List<String> out = new ArrayList<>();
        int n = Math.max(1, max);
        for (Entry e : stack) {
            if (out.size() >= n) {
                break;
            }
            out.add(e.description());
        }
        return out;
    }

    public int size() {
        return stack.size();
    }
}
