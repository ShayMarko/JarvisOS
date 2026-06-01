package com.jarvis.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jarvis.audit.AuditService;
import com.jarvis.brain.Orchestrator;
import com.jarvis.error.JarvisException;
import com.jarvis.input.InputRouter;
import com.jarvis.input.RoutedInput;

/**
 * The Command Engine (spec §5). Routes raw input, dispatches recognised slash
 * commands to their handler, audits the outcome, and returns a structured
 * result for the client. Unrecognised commands and (for now) AI requests are
 * handled gracefully with explanatory messages.
 */
@Service
public class CommandEngine {

    private static final Logger log = LoggerFactory.getLogger(CommandEngine.class);

    private final InputRouter router;
    private final CommandRegistry registry;
    private final AuditService audit;
    private final Orchestrator orchestrator;

    public CommandEngine(InputRouter router, CommandRegistry registry, AuditService audit,
                         Orchestrator orchestrator) {
        this.router = router;
        this.registry = registry;
        this.audit = audit;
        this.orchestrator = orchestrator;
    }

    public CommandResult execute(String rawInput) {
        return execute(rawInput, "default");
    }

    public CommandResult execute(String rawInput, String sessionId) {
        RoutedInput routed = router.route(rawInput);

        return switch (routed.type()) {
            case EMPTY -> CommandResult.message("Type a command (try /help) or ask Jarvis something.");

            case AI_REQUEST -> {
                try {
                    var chat = orchestrator.handle(routed.text(), sessionId);
                    yield CommandResult.ok("chat", chat.answer(), chat);
                } catch (JarvisException e) {
                    yield CommandResult.error(e.getMessage());
                } catch (Exception e) {
                    log.error("Brain failed for input", e);
                    yield CommandResult.error("Brain error: " + e.getMessage());
                }
            }

            case UNKNOWN_COMMAND -> {
                String slash = routed.command().slash();
                audit.record("SLASH_COMMAND", slash, routed.text(), "ERROR", "Unknown command");
                yield CommandResult.error("Unknown command: " + slash + ". Type /help for the list.");
            }

            case SLASH_COMMAND -> dispatch(routed);
        };
    }

    private CommandResult dispatch(RoutedInput routed) {
        CommandContext ctx = routed.command();
        CommandHandler handler = registry.find(ctx.slash()).orElseThrow();
        try {
            CommandResult result = handler.handle(ctx);
            audit.record("SLASH_COMMAND", ctx.slash(), routed.text(),
                    result.status().name(), result.message());
            return result;
        } catch (JarvisException e) {
            // Expected domain error (permission, blocked path, not found, …).
            audit.record("SLASH_COMMAND", ctx.slash(), routed.text(), "ERROR",
                    e.code() + ": " + e.getMessage());
            return CommandResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Command {} failed", ctx.slash(), e);
            audit.record("SLASH_COMMAND", ctx.slash(), routed.text(), "ERROR", e.getMessage());
            return CommandResult.error("Command failed: " + e.getMessage());
        }
    }
}
