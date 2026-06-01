package com.jarvis.command.handlers;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.sandbox.SandboxService;

/** {@code /sandbox} — shows Sandbox Runtime info (spec §5.2, §11.4). */
@Component
public class SandboxHandler implements CommandHandler {

    private final SandboxService sandbox;

    public SandboxHandler(SandboxService sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("sandbox", "/sandbox", List.of("show sandbox"),
                "Show the Sandbox Runtime.", List.of(), List.of(), true, CommandCategory.SECURITY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.message(
                "🧪 Sandbox Runtime ready. Root: " + sandbox.getSandboxRoot()
                + ". Risky commands run here in an isolated, throwaway directory after you approve them "
                + "(via /terminal → /approve).");
    }
}
