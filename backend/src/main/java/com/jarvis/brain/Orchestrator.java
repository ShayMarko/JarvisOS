package com.jarvis.brain;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.PrivacyGuard;
import com.jarvis.audit.AuditService;
import com.jarvis.conversation.ConversationService;
import com.jarvis.conversation.ConversationTurn;
import com.jarvis.model.CostCalculator;
import com.jarvis.model.ModelDescriptor;
import com.jarvis.model.ModelRouter;
import com.jarvis.observability.ObservabilityService;
import com.jarvis.profile.PreferenceLearner;
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
    private final ResponseCache responseCache;
    private final JarvisAiProperties ai;
    private final PrivacyGuard privacyGuard;
    private final PreferenceLearner preferenceLearner;
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

        // Exact-prompt cache: an identical timeless question answered recently → return instantly,
        // skipping a slow model run. Only no-tool answers are ever stored (see below), so this can't
        // replay a stale web/file/tool result.
        ResponseCache.Hit hit = responseCache.lookup(message, System.currentTimeMillis());
        if (hit != null) {
            addStep(steps, onStep, new Step("answer", "Answered from cache", hit.ageSeconds() + "s old"));
            conversations.record(session, "ASSISTANT", hit.answer());
            tasks.finish(task, "General Assistant", hit.answer());
            // model = "cache:<ageSeconds>" so the UI can flag a non-live, dated answer to the user.
            return new ChatResponse(hit.answer(), "General Assistant", steps, task.getId(), 0,
                    "cache:" + hit.ageSeconds());
        }

        String context = contextBuilder.build();

        // Task decomposition: a compound request fans out to parallel sub-agents (spec §6).
        List<PlanStep> plan = planner.plan(message);
        if (plan.size() > 1) {
            return executePlan(message, session, task, steps, history, context, onStep, plan);
        }

        AgentDefinition agent = registry.find(plan.get(0).agentSlug()).orElseGet(() -> selector.select(message));
        addStep(steps, onStep, new Step("agent", "Routed to " + agent.name(), agent.role()));

        ModelDescriptor model = modelRouter.route(agent.slug());
        // Privacy Router: sensitive content stays on-device instead of going to a cloud provider.
        if (!model.local() && privacyGuard.keepLocal(message)) {
            ModelDescriptor localM = modelRouter.localModel();
            if (localM != null) {
                addStep(steps, onStep, new Step("model", "Kept on local model for privacy", "sensitive content detected"));
                model = localM;
            }
        }
        addStep(steps, onStep, new Step("model", "Routed to model " + model.id(), modelRouter.preference().name()));

        long start = System.nanoTime();
        try {
            AgentRun run = runtime.run(agent, message, context, history, onStep, model);
            steps.addAll(run.steps());
            // Self-correcting build loop: if files landed under Projects/, verify + auto-fix (bounded).
            run = verifyAndFix(agent, message, context, history, onStep, model, run, steps);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            double cost = CostCalculator.cost(model, run.promptTokens(), run.completionTokens());
            // Cache only PURE-knowledge answers (no tool was used this run) — these are timeless and
            // safe to replay; tool/web/file answers are not cached (could be time-sensitive).
            boolean usedTools = run.steps().stream().anyMatch(s -> "tool".equals(s.kind()));
            if (!usedTools) {
                responseCache.put(message, run.answer(), System.currentTimeMillis());
            }
            conversations.record(session, "ASSISTANT", run.answer());
            tasks.finish(task, agent.name(), run.answer());
            // Preference-learning loop: notice durable facts about the user and OFFER to remember them (consent + local).
            preferenceLearner.observe(message, run.answer());
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

    private static final java.util.regex.Pattern WROTE_PROJECT =
            java.util.regex.Pattern.compile("Projects/([A-Za-z0-9._-]+)/");

    /**
     * Self-correcting build loop (the tester ⇄ developer cycle). If the developer's run wrote files
     * under {@code Projects/<app>/}, the Test agent verifies the build (via run_in_sandbox) and ends
     * its reply with {@code VERDICT: PASS|FAIL}. On FAIL, the SAME developer agent is re-dispatched
     * with the tester's errors and the project is re-verified — up to {@code buildVerifyMaxIters} times.
     * Each step streams to the HUD ("→ Test Agent (verify)" / "→ <dev> (fixing)"). Gated on a real model
     * and a detected project, so non-build turns are untouched.
     */
    private AgentRun verifyAndFix(AgentDefinition dev, String message, String context, List<ChatMessage> history,
            Consumer<Step> onStep, ModelDescriptor model, AgentRun firstRun, List<Step> steps) {
        int max = ai.getBuildVerifyMaxIters();
        String project = detectProjectName(firstRun.steps());
        if (max <= 0 || project == null || !realModel()) {
            return firstRun;
        }
        AgentDefinition tester = registry.find("test").orElse(dev);
        AgentRun current = firstRun;
        for (int iter = 1; iter <= max; iter++) {
            addStep(steps, onStep, new Step("agent", "→ Test Agent (verify)", "Projects/" + project));
            String verifyPrompt = "Verify the project at Projects/" + project + ". Use run_in_sandbox to compile/build "
                    + "it or run its tests (do NOT try to launch a GUI window — it would hang). Then end your reply with "
                    + "ONE line: 'VERDICT: PASS' if it builds/tests cleanly, or 'VERDICT: FAIL' followed by the exact errors.";
            AgentRun verify = runtime.run(tester, verifyPrompt, context, history, onStep, model);
            steps.addAll(verify.steps());
            if (!verdictFail(verify.answer())) {
                addStep(steps, onStep, new Step("answer", "✓ Build verified (attempt " + iter + ")", null));
                return current;
            }
            addStep(steps, onStep, new Step("agent", "→ " + dev.name() + " (fixing)", "attempt " + iter));
            String fixPrompt = message + "\n\nThe build did NOT pass verification. The tester reported:\n"
                    + clip(verify.answer()) + "\nFix the files under Projects/" + project + " so it builds/tests cleanly.";
            current = runtime.run(dev, fixPrompt, context, history, onStep, model);
            steps.addAll(current.steps());
        }
        addStep(steps, onStep, new Step("answer", "Verification still failing after " + max + " fix attempts", null));
        return current;
    }

    /** The project folder name from a write_file step ("Wrote Projects/<name>/…"), or null if none. */
    private String detectProjectName(List<Step> steps) {
        for (Step s : steps) {
            if (!"tool".equals(s.kind()) || s.detail() == null) {
                continue;
            }
            java.util.regex.Matcher m = WROTE_PROJECT.matcher(s.detail());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /** Only re-dispatch on an explicit FAIL verdict (avoids looping on ambiguous output). */
    private static boolean verdictFail(String answer) {
        if (answer == null) {
            return false;
        }
        String a = answer.toLowerCase();
        return !a.contains("verdict: pass") && a.contains("verdict: fail");
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 1500 ? s.substring(0, 1500) + "…" : s;
    }

    /** A real reasoning model is active (not the offline mock) — gates the verify loop. */
    private boolean realModel() {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return p.equals("ollama")
                || ((p.equals("claude") || p.equals("anthropic")) && notBlank(ai.getAnthropicApiKey()))
                || (p.equals("openai") && notBlank(ai.getOpenaiApiKey()));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Runs a multi-step plan: sub-agents in parallel, then merges their answers. */
    private ChatResponse executePlan(String message, String session, Task task, List<Step> steps,
            List<ChatMessage> history, String context, Consumer<Step> onStep, List<PlanStep> plan) {
        final Object lock = new Object();
        // Thread-safe step emit (parallel sub-agents share the steps list + SSE emitter).
        Consumer<Step> emit = step -> { synchronized (lock) { steps.add(step); if (onStep != null) onStep.accept(step); } };

        boolean hasDeps = plan.stream().anyMatch(p -> !p.dependsOn().isEmpty());
        emit.accept(new Step("plan",
                "Decomposed into " + plan.size() + " sub-tasks (" + (hasDeps ? "pipeline — dependencies honored" : "running in parallel") + ")",
                plan.stream().map(PlanStep::agentName).collect(Collectors.joining(", "))));

        ModelDescriptor model = modelRouter.route("general");
        long start = System.nanoTime();
        try {
            // Build one future per step; a step starts once all the steps it depends on complete,
            // and receives their outputs as extra context. Independent steps still run in parallel.
            Map<String, CompletableFuture<SubResult>> byId = new LinkedHashMap<>();
            for (PlanStep ps : plan) {
                List<CompletableFuture<SubResult>> deps = ps.dependsOn().stream()
                        .map(byId::get).filter(Objects::nonNull).toList();
                CompletableFuture<Void> gate = deps.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.allOf(deps.toArray(CompletableFuture[]::new));
                CompletableFuture<SubResult> fut = gate.thenApplyAsync(v -> {
                    AgentDefinition a = registry.find(ps.agentSlug()).orElseGet(registry::general);
                    String stepContext = withDependencyOutputs(context, deps);
                    ModelDescriptor subModel = modelRouter.route(a.slug());   // pick the model per sub-task
                    if (!subModel.local() && privacyGuard.keepLocal(ps.task())) {   // sensitive → stay on-device
                        ModelDescriptor localM = modelRouter.localModel();
                        if (localM != null) {
                            subModel = localM;
                        }
                    }
                    emit.accept(new Step("agent", "→ " + a.name(), ps.task() + " · " + subModel.id()));
                    AgentRun run = runtime.run(a, ps.task(), stepContext, history, null, subModel);
                    emit.accept(new Step("tool", "✓ " + a.name(), truncate(run.answer())));
                    return new SubResult(ps, run);
                }, PLAN_EXEC);
                byId.put(ps.id(), fut);
            }
            List<SubResult> results = new ArrayList<>();
            for (PlanStep ps : plan) {
                results.add(byId.get(ps.id()).join());
            }

            int promptTokens = results.stream().mapToInt(r -> r.run().promptTokens()).sum();
            int completionTokens = results.stream().mapToInt(r -> r.run().completionTokens()).sum();
            String merged = synthesize(results);
            emit.accept(new Step("answer", "Merged " + results.size() + " results", null));

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            double cost = CostCalculator.cost(model, promptTokens, completionTokens);
            conversations.record(session, "ASSISTANT", merged);
            tasks.finish(task, "Planner", merged);
            preferenceLearner.observe(message, merged);
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

    /** Append the (already-completed) dependency step outputs to a step's context. */
    private String withDependencyOutputs(String baseContext, List<CompletableFuture<SubResult>> deps) {
        if (deps.isEmpty()) {
            return baseContext;
        }
        StringBuilder sb = new StringBuilder(baseContext == null ? "" : baseContext);
        sb.append("\n\nResults from earlier steps you can build on:\n");
        for (CompletableFuture<SubResult> d : deps) {
            SubResult r = d.join();   // already complete: the gate awaited it
            sb.append("• ").append(r.step().task()).append(":\n").append(r.run().answer()).append("\n\n");
        }
        return sb.toString().strip();
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
