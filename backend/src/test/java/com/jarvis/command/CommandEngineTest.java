package com.jarvis.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jarvis.audit.AuditService;
import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.input.InputRouter;

class CommandEngineTest {

    /** A trivial handler used to exercise the dispatch path. */
    private static class PingHandler implements CommandHandler {
        @Override
        public CommandDefinition definition() {
            return CommandDefinition.simple("ping", "/ping", "Ping.", CommandCategory.SYSTEM);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            return CommandResult.message("pong");
        }
    }

    private final Orchestrator orchestrator = mock(Orchestrator.class);

    private CommandEngine engine() {
        CommandRegistry registry = new CommandRegistry(List.of(new PingHandler()));
        InputRouter router = new InputRouter(registry);
        AuditService audit = mock(AuditService.class);
        return new CommandEngine(router, registry, audit, orchestrator);
    }

    @Test
    void emptyInputPromptsForHelp() {
        CommandResult result = engine().execute("   ");
        assertThat(result.status()).isEqualTo(CommandResult.Status.OK);
        assertThat(result.message()).containsIgnoringCase("help");
    }

    @Test
    void freeTextGoesToTheBrain() {
        when(orchestrator.handle(anyString(), anyString()))
                .thenReturn(new ChatResponse("answer from brain", "General Assistant",
                        List.of(), "task_1", 12, "mock"));
        CommandResult result = engine().execute("summarise my emails");
        assertThat(result.type()).isEqualTo("chat");
        assertThat(result.message()).isEqualTo("answer from brain");
    }

    @Test
    void unknownCommandIsRejected() {
        CommandResult result = engine().execute("/nope");
        assertThat(result.status()).isEqualTo(CommandResult.Status.ERROR);
        assertThat(result.message()).containsIgnoringCase("unknown");
    }

    @Test
    void knownCommandIsDispatched() {
        CommandResult result = engine().execute("/ping");
        assertThat(result.message()).isEqualTo("pong");
    }
}
