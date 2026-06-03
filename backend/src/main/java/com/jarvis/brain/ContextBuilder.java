package com.jarvis.brain;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.jarvis.skill.SkillService;

/**
 * Assembles context for the Brain (spec §6 "Context Builder").
 *
 * <p>Nothing about the user is force-injected every turn — neither the (possibly long)
 * About-Me profile nor the Memory facts. The agent retrieves on demand: {@code profile_search}
 * for who-they-are questions, {@code memory_search} for stored notes. This keeps every prompt
 * lean (token cost) AND avoids two "about me" sources fighting — the profile is the single
 * authoritative identity doc; Memory holds discrete notes/reminders.
 *
 * <p>The ONE thing injected (when present) is a COMPACT roster of learned skills — just
 * "name — description" lines — so the model knows which taught procedures it can perform and can
 * {@code skill_search} for the full steps on demand. Names + one-liners only, so it stays cheap.
 */
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final int MAX_SKILLS = 25;

    private final SkillService skills;

    public String build() {
        String roster = skills.roster(MAX_SKILLS);
        if (roster.isBlank()) {
            return "";
        }
        return "Skills you've been taught (use skill_search to recall the steps, then perform them with your tools):\n"
                + roster;
    }
}
