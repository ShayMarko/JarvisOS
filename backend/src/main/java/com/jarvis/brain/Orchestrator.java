package com.jarvis.brain;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jarvis.agent.AgentDefinition;
import com.jarvis.agent.AgentRegistry;
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

    /** Bounded pool so parallel sub-agents can't spawn unbounded threads. */
    private static final ExecutorService PLAN_EXEC =
            Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

    private final AgentSelector selector;
    private final AgentRuntime runtime;
    private final AgentRegistry registry;
    private final Planner planner;
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
        return handle(message, sessionId, null);
    }

    /**
     * Same as {@link #handle} but emits each {@link Step} to {@code onStep} the
     * moment it happens, so a streaming endpoint can show Jarvis thinking live.
     */
    public ChatResponse handle(String message, String sessionId, java.util.function.Consumer<Step> onStep) {
        String session = sessionId == null || sessionId.isBlank() ? DEFAULT_SESSION : sessionId;
        Task task = tasks.start(message);
        List<Step> steps = new ArrayList<>();
        addStep(steps, onStep, new Step("intent", "Understood the request", null));

        // Conversation continuity: prior turns become history the agent can follow (#8).
        List<ChatMessage> history = new ArrayList<>();
        for (ConversationTurn turn : conversations.recent(session)) {
            history.add("ASSISTANT".equals(turn.getRole())
                    ? ChatMessage.assistant(turn.getContent())
                    : ChatMessage.user(turn.getContent()));
        }
        if (!history.isEmpty()) {
            addStep(steps, onStep, new Step("intent", "Recalled " + history.size() + " prior turn(s)", null));
        }
        conversations.record(session, "USER", message);

        String context = contextBuilder.build();

        // Task decomposition: a compound request fans out to parallel sub-agents (spec §6).
        List<PlanStep> plan = planner.plan(message);
        if (plan.size() > 1) {
            return executePlan(message, session, task, steps, history, context, onStep, plan);
        }

        AgentDefinition agent = registry.find(plan.get(0).agentSlug()).orElseGet(() -> selector.select(message));
        addStep(steps, onStep, new Step("agent", "Routed to " + agent.name(), agent.role()));

        ModelDescriptor model = modelRouter.route(agent.slug());
        addStep(steps, onStep, new Step("model", "Routed to model " + model.id(), modelRouter.preference().name()));

        long start = System.nanoTime();
        try {
            AgentRun run = runtime.run(agent, message, context, history, onStep);
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

    private void addStep(List<Step> steps, java.util.function.Consumer<Step> onStep, Step step) {
        steps.add(step);
        if (onStep != null) {
            onStep.accept(step);
        }
    }

    /** Runs a multi-step plan: sub-agents in parallel, then merges their answers. */
    private ChatResponse executePlan(String message, String session, Task task, List<Step> steps,
            List<ChatMessage> history, String context, Consumer<Step> onStep, List<PlanStep> plan) {
        final Object lock = new Object();
        // Thread-safe step emit (parallel sub-agents share the steps list + SSE emitter).
        Consumer<Step> emit = step -> { synchronized (lock) { steps.add(step); if (onStep != null) onStep.accept(step); } };

        emit.accept(new Step("plan", "Decomposed into " + plan.size() + " sub-tasks (running in parallel)",
                plan.stream().map(PlanStep::agentName).collect(Collectors.joining(", "))));

        ModelDescriptor model = modelRouter.route("general");
        long start = System.nanoTime();
        try {
            List<CompletableFuture<SubResult>> futures = new ArrayList<>();
            for (PlanStep ps : plan) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    AgentDefinition a = registry.find(ps.agentSlug()).orElseGet(registry::general);
                    emit.accept(new Step("agent", "→ " + a.name(), ps.task()));
                    AgentRun run = runtime.run(a, ps.task(), context, history);
                    emit.accept(new Step("tool", "✓ " + a.name(), truncate(run.answer())));
                    return new SubResult(ps, run);
                }, PLAN_EXEC));
            }
            List<SubResult> results = new ArrayList<>();
            for (CompletableFuture<SubResult> f : futures) {
                results.add(f.join());
            }

            int promptTokens = results.stream().mapToInt(r -> r.run().promptTokens()).sum();
            int completionTokens = results.stream().mapToInt(r -> r.run().completionTokens()).sum();
            String merged = synthesize(results);
            emit.accept(new Step("answer", "Merged " + results.size() + " results", null));

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            double cost = CostCalculator.cost(model, promptTokens, completionTokens);
            conversations.record(session, "ASSISTANT", merged);
            tasks.finish(task, "Planner", merged);
            observability.record(task.getId(), session, "Planner", model.id(), message, merged, "OK",
                    promptTokens, completionTokens, cost, durationMs, writeSteps(steps));
            audit.record("BRAIN", "planner", message, "OK",
                    "task=" + task.getId() + "; subtasks=" + results.size() + "; tokens=" + (promptTokens + completionTokens));
            String modelName = results.isEmpty() ? model.id() : results.get(0).run().model();
            return new ChatResponse(merged, "Planner (" + results.size() + " agents)", steps, task.getId(),
                    promptTokens + completionTokens, modelName);
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            tasks.fail(task, e.getMessage());
            observability.record(task.getId(), session, "Planner", model.id(), message, "", "FAILED",
                    0, 0, 0, durationMs, writeSteps(steps));
            audit.record("BRAIN", "planner", message, "ERROR", e.getMessage());
            throw e;
        }
    }

    private String synthesize(List<SubResult> results) {
        StringBuilder sb = new StringBuilder();
        for (SubResult r : results) {
            sb.append("### ").append(r.step().agentName()).append(" — ").append(r.step().task()).append('\n');
            sb.append(r.run().answer()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private record SubResult(PlanStep step, AgentRun run) {}

    private String writeSteps(List<Step> steps) {
        try {
            return mapper.writeValueAsString(steps);
        } catch (Exception e) {
            return "[]";
        }
    }
}
