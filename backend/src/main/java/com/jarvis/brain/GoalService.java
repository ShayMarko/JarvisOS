package com.jarvis.brain;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryDraft;
import com.jarvis.memory.MemoryService;

import lombok.RequiredArgsConstructor;

/**
 * The north-star / OKR store — a single persistent GOAL the autonomous loops (Coordinator, proactive
 * initiative) plan toward, so the business has direction instead of drifting. Backed by the Memory store
 * (category {@code goal}, title {@code north-star}) so it survives restarts, shows up in the Memory Manager,
 * and the model can read/update it like any other memory. One active goal at a time (latest wins).
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    static final String CATEGORY = "goal";
    static final String TITLE = "north-star";

    private final MemoryService memory;

    /** The current north-star text (incl. any key results), or null if none set. */
    public String current() {
        return find().map(Memory::getContent).orElse(null);
    }

    /** Set/replace the north-star. Returns the saved text. */
    public String set(String text) {
        String body = text == null ? "" : text.strip();
        Optional<Memory> existing = find();
        if (existing.isPresent()) {
            memory.update(existing.get().getId(), new MemoryDraft(
                    CATEGORY, TITLE, body, "goal", 1.0, null, null, null, true));
        } else {
            memory.create(new MemoryDraft(CATEGORY, TITLE, body, "goal", 1.0, null, null, null, true));
        }
        return body;
    }

    private Optional<Memory> find() {
        return memory.list("").stream()   // already ordered updatedAt desc → first match is the latest
                .filter(m -> CATEGORY.equalsIgnoreCase(m.getCategory()) && TITLE.equalsIgnoreCase(m.getTitle()))
                .findFirst();
    }
}
