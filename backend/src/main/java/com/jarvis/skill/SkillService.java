package com.jarvis.skill;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import com.jarvis.common.Ids;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Stores and recalls the skills Jarvis has been taught. A skill is a named procedure ("how to do X")
 * that Jarvis performs later with its existing tools — so it self-extends without us shipping code.
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository repository;

    /** Teach (or update) a skill. Upserts by name so re-teaching refines it instead of duplicating. */
    @Transactional
    public Skill learn(String name, String description, String instructions) {
        Skill s = repository.findByNameIgnoreCase(name.trim()).orElseGet(Skill::new);
        if (s.getId() == null) {
            s.setId(Ids.generate("skl", 10));
            s.setCreatedAt(Instant.now());
            s.setName(name.trim());
        }
        s.setDescription(description == null ? "" : description.trim());
        s.setInstructions(instructions == null ? "" : instructions.trim());
        s.setUpdatedAt(Instant.now());
        return repository.save(s);
    }

    public List<Skill> all() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    /** Compact roster ("name — description") for the context hint, so the model knows what it can do. */
    public String roster(int max) {
        List<Skill> all = all();
        if (all.isEmpty()) {
            return "";
        }
        return all.stream().limit(max)
                .map(s -> s.getName() + " — " + s.getDescription())
                .collect(Collectors.joining("\n"));
    }

    /** Best-matching skill's full instructions for a query (keyword overlap); records the recall. */
    @Transactional
    public String findInstructions(String query) {
        Set<String> q = terms(query);
        Skill best = null;
        long bestScore = -1;
        for (Skill s : all()) {
            long score = overlap(q, terms(s.getName() + " " + s.getDescription() + " " + s.getInstructions()));
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best == null || bestScore <= 0) {
            return null;   // no learned skill matches
        }
        best.setUses(best.getUses() + 1);
        repository.save(best);
        return "Skill \"" + best.getName() + "\" (" + best.getDescription() + "):\n" + best.getInstructions();
    }

    private static Set<String> terms(String text) {
        return Arrays.stream(text == null ? new String[0] : text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toSet());
    }

    private static long overlap(Set<String> a, Set<String> b) {
        return a.stream().filter(b::contains).count();
    }
}
