package com.jarvis.brain;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Assembles context for the Brain (spec §6 "Context Builder").
 *
 * <p>Nothing about the user is force-injected every turn — neither the (possibly long)
 * About-Me profile nor the Memory facts. The agent retrieves on demand: {@code profile_search}
 * for who-they-are questions, {@code memory_search} for stored notes. This keeps every prompt
 * lean (token cost) AND avoids two "about me" sources fighting — the profile is the single
 * authoritative identity doc; Memory holds discrete notes/reminders.
 */
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    public String build() {
        return "";
    }
}
