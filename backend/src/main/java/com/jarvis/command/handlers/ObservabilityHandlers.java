package com.jarvis.command.handlers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.model.ModelCatalog;
import com.jarvis.model.ModelRouter;
import com.jarvis.observability.ObservabilityService;

/** Phase 10 commands: /models, /debugger, /costs, /replay (spec §5.2, §6, §13). */
public final class ObservabilityHandlers {

    private ObservabilityHandlers() {}

    @Component
    public static class ModelsHandler implements CommandHandler {
        private final ModelCatalog catalog;
        private final ModelRouter router;

        public ModelsHandler(ModelCatalog catalog, ModelRouter router) {
            this.catalog = catalog;
            this.router = router;
        }

        @Override
        public CommandDefinition definition() {
            return CommandDefinition.simple("models", "/models",
                    "Show the model catalog and router preference.", CommandCategory.MONITORING);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("models", catalog.all());
            data.put("active", catalog.active().id());
            data.put("preference", router.preference().name());
            return CommandResult.ok("models", "Model Manager", data);
        }
    }

    @Component
    public static class DebuggerHandler implements CommandHandler {
        private final ObservabilityService observability;

        public DebuggerHandler(ObservabilityService observability) {
            this.observability = observability;
        }

        @Override
        public CommandDefinition definition() {
            return new CommandDefinition("debugger", "/debugger", List.of("agent debugger", "traces"),
                    "Open the Agent Debugger (run traces).", List.of(),
                    List.of(), true, CommandCategory.MONITORING);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            return CommandResult.ok("runs", "Agent Debugger", observability.recent(50));
        }
    }

    @Component
    public static class CostsHandler implements CommandHandler {
        private final ObservabilityService observability;

        public CostsHandler(ObservabilityService observability) {
            this.observability = observability;
        }

        @Override
        public CommandDefinition definition() {
            return CommandDefinition.simple("costs", "/costs",
                    "Show token and cost usage.", CommandCategory.MONITORING);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            return CommandResult.ok("costs", "Cost / Token Monitor", observability.costSummary(200));
        }
    }

    @Component
    public static class ReplayHandler implements CommandHandler {
        private final ObservabilityService observability;
        private final Orchestrator orchestrator;

        public ReplayHandler(ObservabilityService observability, Orchestrator orchestrator) {
            this.observability = observability;
            this.orchestrator = orchestrator;
        }

        @Override
        public CommandDefinition definition() {
            return new CommandDefinition("replay", "/replay", List.of(),
                    "Replay a recorded run: /replay <runId>.", List.of("runId"),
                    List.of(), true, CommandCategory.MONITORING);
        }

        @Override
        public CommandResult handle(CommandContext context) {
            String id = context.argLine().trim();
            if (id.isEmpty()) {
                return CommandResult.error("Usage: /replay <runId> (see /debugger)");
            }
            ChatResponse replayed = orchestrator.handle(observability.get(id).getRequest());
            return CommandResult.ok("chat", replayed.answer(), replayed);
        }
    }
}
