package com.jarvis.input;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandRegistry;

/**
 * The Input Router (spec §4). It is the first decision point for any input:
 * does it begin with a recognised slash command (deterministic path) or is it
 * a free-form AI request? Later phases extend this with workflow/approval/
 * sandbox/model-routing classification.
 */
@Component
@RequiredArgsConstructor
public class InputRouter {

    private final CommandRegistry registry;


    public RoutedInput route(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return RoutedInput.empty();
        }
        String raw = rawInput.strip();

        if (raw.startsWith("/")) {
            String[] tokens = raw.split("\\s+");
            String slash = tokens[0];
            List<String> args = tokens.length > 1
                    ? Arrays.asList(tokens).subList(1, tokens.length)
                    : List.of();
            // "Slash command always wins": a known command never reaches the Brain.
            return registry.isCommand(slash)
                    ? RoutedInput.slashCommand(raw, slash, args)
                    : RoutedInput.unknownCommand(raw, slash, args);
        }

        return RoutedInput.aiRequest(raw);
    }
}
