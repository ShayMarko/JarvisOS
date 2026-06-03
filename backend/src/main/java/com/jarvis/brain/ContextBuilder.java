package com.jarvis.brain;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.jarvis.profile.ProfileService;
import com.jarvis.skill.SkillService;

/**
 * Assembles context for the Brain (spec §6 "Context Builder").
 *
 * <p>Two lean, always-present pieces (so they survive any agent/model and don't depend on the model
 * choosing to call a tool):
 * <ol>
 *   <li>A COMPACT identity block from the About-Me profile (Identity + Snapshot only — name, location,
 *       languages, a one-line summary). This guarantees "who am I / what's my name" is always answerable.
 *       The full, longer, partly-sensitive profile stays on demand via {@code profile_search}.</li>
 *   <li>A COMPACT roster of learned skills ("name — description" lines) so the model knows which taught
 *       procedures it can perform and can {@code skill_search} for the steps.</li>
 * </ol>
 * Memory facts are NOT force-injected — the agent retrieves those on demand via {@code memory_search}.
 * Identity basics are deliberately injected because they're tiny and needed constantly; the deep profile
 * and discrete notes are pulled only when relevant, keeping prompts lean.
 */
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final int MAX_SKILLS = 25;

    private final ProfileService profile;
    private final SkillService skills;

    public String build() {
        StringBuilder ctx = new StringBuilder();

        String identity = profile.compactIdentity();
        if (!identity.isBlank()) {
            ctx.append("About the user you're assisting (their profile — call profile_search for anything deeper):\n")
                    .append(identity).append("\n\n");
        }

        String roster = skills.roster(MAX_SKILLS);
        if (!roster.isBlank()) {
            ctx.append("Skills you've been taught (use skill_search to recall the steps, then perform them with your tools):\n")
                    .append(roster);
        }

        return ctx.toString().strip();
    }
}
