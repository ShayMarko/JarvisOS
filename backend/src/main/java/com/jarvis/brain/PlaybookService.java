package com.jarvis.brain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryDraft;
import com.jarvis.memory.MemoryService;

import lombok.RequiredArgsConstructor;

/**
 * Skill/playbook formation: when the Brain successfully solves a multi-step request, the winning PLAN is
 * remembered (durably, in Memory under category "playbook"). On a later NEAR-IDENTICAL request the proven
 * plan is replayed as the skeleton — skipping re-planning, which is faster and more consistent. Matching is
 * deliberately conservative (high token overlap) so a saved plan only fires for genuinely similar requests;
 * the sub-agents still do the real work per step. Cross-session and durable (complements the in-session
 * ResponseCache and the user-taught SkillService).
 */
@Service
@RequiredArgsConstructor
public class PlaybookService {

    private static final Logger log = LoggerFactory.getLogger(PlaybookService.class);
    private static final String CATEGORY = "playbook";
    private static final double MATCH_THRESHOLD = 0.8;   // near-identical only
    private static final int MAX_KEY = 120;

    private final MemoryService memory;
    private final ObjectMapper mapper;

    /** Remember the plan that solved this request (no-op for single-step plans or duplicates). */
    public void capture(String message, List<PlanStep> plan) {
        if (plan == null || plan.size() < 2 || message == null || message.isBlank()) {
            return;
        }
        String key = normalize(message);
        if (key.isBlank()) {
            return;
        }
        // Skip if we already hold a playbook for this exact normalized intent.
        boolean exists = playbooks().stream().anyMatch(m -> key.equals(m.getTitle()));
        if (exists) {
            return;
        }
        try {
            String json = mapper.writeValueAsString(plan);
            memory.create(new MemoryDraft(CATEGORY, key, json, "playbook", 0.9, null, null, null, true));
            log.info("Captured playbook for intent '{}' ({} steps)", key, plan.size());
        } catch (Exception e) {
            log.warn("Could not capture playbook: {}", e.getMessage());
        }
    }

    /** The proven plan for a near-identical past request, if one exists. */
    public Optional<List<PlanStep>> match(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        Set<String> q = tokens(message);
        if (q.isEmpty()) {
            return Optional.empty();
        }
        Memory best = null;
        double bestScore = 0;
        for (Memory m : playbooks()) {
            double s = jaccard(q, tokens(m.getTitle()));
            if (s > bestScore) {
                bestScore = s;
                best = m;
            }
        }
        if (best == null || bestScore < MATCH_THRESHOLD) {
            return Optional.empty();
        }
        try {
            PlanStep[] steps = mapper.readValue(best.getContent(), PlanStep[].class);
            if (steps == null || steps.length < 2) {
                return Optional.empty();
            }
            log.info("Replaying playbook '{}' (match {}%)", best.getTitle(), Math.round(bestScore * 100));
            return Optional.of(Arrays.asList(steps));
        } catch (Exception e) {
            log.warn("Could not parse stored playbook: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<Memory> playbooks() {
        return memory.list("").stream().filter(m -> CATEGORY.equals(m.getCategory())).toList();
    }

    // ---- pure helpers (testable) ----------------------------------------------------------------------

    static String normalize(String s) {
        String n = (s == null ? "" : s).toLowerCase().replaceAll("[^a-z0-9]+", " ").strip();
        return n.length() > MAX_KEY ? n.substring(0, MAX_KEY).strip() : n;
    }

    static Set<String> tokens(String s) {
        return Arrays.stream(normalize(s).split("\\s+"))
                .filter(t -> t.length() > 2)   // drop trivial stop-ish tokens
                .collect(Collectors.toSet());
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        long inter = a.stream().filter(b::contains).count();
        long union = a.size() + b.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }
}
