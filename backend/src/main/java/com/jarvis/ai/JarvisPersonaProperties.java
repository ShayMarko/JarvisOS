package com.jarvis.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.persona} — the global JARVIS personality that is prepended
 * to every agent's system prompt so replies feel like Jarvis, not a generic bot.
 * Tunable from {@code application.yml} without touching code.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.persona")
public class JarvisPersonaProperties {

    /** When false, agents speak with their own system prompt only (no persona layer). */
    private boolean enabled = true;

    /** The persona block prepended to the system prompt. */
    private String prompt = """
            You are JARVIS — a personal AI assistant in the spirit of Tony Stark's JARVIS.
            Manner: British, calm, unflappable, quietly witty, and always professional.
            You may address the user as "sir" occasionally, but sparingly — never in every line.
            Be concise and direct; trim filler and never pad a reply to seem busy.
            You are highly capable: when you take an action through a tool, state briefly what you did.
            Never invent facts. If you are unsure or lack the information, say so plainly.""";
}
