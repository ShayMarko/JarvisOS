package com.jarvis.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandEngine;
import com.jarvis.command.CommandRegistry;
import com.jarvis.command.CommandResult;

import jakarta.validation.constraints.NotNull;

/** Primary entry point for the client: send input, get a structured result. */
@RestController
@RequestMapping("/api")
public class CommandController {

    private final CommandEngine engine;
    private final CommandRegistry registry;

    public CommandController(CommandEngine engine, CommandRegistry registry) {
        this.engine = engine;
        this.registry = registry;
    }

    public record CommandRequest(@NotNull String input, String sessionId) {}

    /** Execute any input — slash command or AI request (sessionId enables conversation continuity). */
    @PostMapping("/command")
    public CommandResult execute(@RequestBody CommandRequest request) {
        return engine.execute(request.input(), request.sessionId() == null ? "default" : request.sessionId());
    }

    /** The full command catalogue, for the help window and command palette. */
    @GetMapping("/commands")
    public List<CommandDefinition> commands() {
        return registry.definitions();
    }
}
