package com.jarvis.brain;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.jarvis.agent.AgentDefinition;
import com.jarvis.agent.AgentRun;
import com.jarvis.agent.AgentRuntime;
import com.jarvis.agent.AgentSelector;
import com.jarvis.agent.Step;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ChatMessage;
import com.jarvis.audit.AuditService;
import com.jarvis.conversation.ConversationService;
import com.jarvis.conversation.ConversationTurn;
import com.jarvis.model.CostCalculator;
import com.jarvis.model.ModelDescriptor;
import com.jarvis.model.ModelRouter;
import com.jarvis.observability.ObservabilityService;
import com.jarvis.task.Task;
import com.jarvis.task.TaskService;

/**
 * The Jarvis Brain / Orchestrator (spec §6). Understands the request, builds
 * context (memory + conversation history), selects an agent, runs it through the
 * tool-calling loop, records the task and the conversation turns, and returns the
 * answer with a transparent step trace. It coordinates — it does not do the work.
 */
@Service
@RequiredArgsConstructor
public class Orchestrator {

    private static final String DEFAULT_SESSION = "default";

    private final AgentSelector selector;
    private final AgentRuntime runtime;
    private final ContextBuilder contextBuilder;
    private final ConversationService conversations;
    private final TaskService tasks;
    private final AuditService audit;
    private final ModelRouter modelRouter;
    private final ObservabilityService observability;
    private final ObjectMapper mapper;


    public ChatResponse handle(String message) {
        return handle(message, DEFAULT_SESSION);
    }

    public ChatResponse handle(String message, String sessionId) {
        String session = sessionId == null || sessionId.isBlank() ? DEFAULT_SESSION : sessionId;
        Task task = tasks.start(message);
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("intent", "Understood the request", null));

        // Conversation continuity: prior turns become history the agent can follow (#8).
        List<ChatMessage> history = new ArrayList<>();
        for (ConversationTurn turn : conversations.recent(session)) {
            history.add("ASSISTANT".equals(turn.getRole())
                    ? ChatMessage.assistant(turn.getContent())
                    : ChatMessage.user(turn.getContent()));
        }
        if (!history.isEmpty()) {
            steps.add(new Step("intent", "Recalled " + history.size() + " prior turn(s)", null));
        }
        conversations.record(session, "USER", message);

        String context = contextBuilder.build();
        AgentDefinition agent = selector.select(message);
        steps.add(new Step("agent", "Routed to " + agent.name(), agent.role()));

        ModelDescriptor model = modelRouter.route(agent.slug());
        steps.add(new Step("model", "Routed to model " + model.id(), modelRouter.preference().name()));

        long start = System.nanoTime();
        try {
            AgentRun run = runtime.run(agent, message, context, history);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            double cost = CostCalculator.cost(model, run.promptTokens(), run.completionTokens());
            steps.addAll(run.steps());
            conversations.record(session, "ASSISTANT", run.answer());
            tasks.finish(task, agent.name(), run.answer());
            observability.record(task.getId(), session, agent.name(), model.id(), message, run.answer(), "OK",
                    run.promptTokens(), run.completionTokens(), cost, durationMs, writeSteps(steps));
            audit.record("BRAIN", agent.slug(), message, "OK",
                    "task=" + task.getId() + "; model=" + model.id() + "; tokens=" + run.tokens()
                    + "; cost=$" + cost);
            return new ChatResponse(run.answer(), agent.name(), steps, task.getId(), run.tokens(), run.model());
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            tasks.fail(task, e.getMessage());
            observability.record(task.getId(), session, agent.name(), model.id(), message, "", "FAILED",
                    0, 0, 0, durationMs, writeSteps(steps));
            audit.record("BRAIN", agent.slug(), message, "ERROR", e.getMessage());
            throw e;
        }
    }

    private String writeSteps(List<Step> steps) {
        try {
            return mapper.writeValueAsString(steps);
        } catch (Exception e) {
            return "[]";
        }
    }
}
