package com.jarvis.agent;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Picks the most appropriate agent for a request (the Brain's routing step).
 * Phase 6 uses ordered keyword rules — the first matching rule wins, else the
 * General Assistant. (A real model can do this selection in later phases.)
 */
@Component
@RequiredArgsConstructor
public class AgentSelector {

    /** An ordered routing rule: if the message contains any keyword, route to {@code slug}. */
    private record Rule(String slug, List<String> keywords) {}

    private static final List<Rule> RULES = List.of(
            new Rule("system", List.of("cpu", "ram", "system status", "resource", "health")),
            new Rule("knowledge", List.of("remember", "memory", "preference", "know about me")),
            new Rule("email", List.of("email", "inbox", "mail", "reply to")),
            new Rule("calendar", List.of("calendar", "schedule", "meeting", "event", "agenda")),
            new Rule("data", List.of("analyse", "analyze", "data", "csv", "spreadsheet")),
            new Rule("code", List.of("code", "function", "bug", "class ", "compile", "stack trace")),
            new Rule("research", List.of("document", "docs", "according to", "knowledge base",
                    "on the web", "web search", "research", "look up", "summarise", "summarize", "find")),
            new Rule("files", List.of("file", "files", "folder", "directory", "read", "open", "explorer")));

    private final AgentRegistry registry;

    public AgentDefinition select(String message) {
        String m = message.toLowerCase();
        return RULES.stream()
                .filter(rule -> rule.keywords().stream().anyMatch(m::contains))
                .map(rule -> registry.find(rule.slug()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst()
                .orElseGet(registry::general);
    }
}
