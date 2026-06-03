package com.jarvis.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.JarvisPersonaProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.ai.ToolCall;
import com.jarvis.ai.ToolSpec;
import com.jarvis.ai.tools.Tool;
import com.jarvis.ai.tools.ToolRegistry;

/**
 * The agent runtime / tool-calling loop (spec §15 "we build the agent loop"):
 * ask the model → if it wants tools, run them and feed results back → repeat
 * until it produces a final answer or the step budget is exhausted.
 */
@Service
public class AgentRuntime {

    private final LanguageModel model;
    private final ToolRegistry tools;
    private final int maxSteps;
    private final JarvisPersonaProperties persona;
    private final JarvisAiProperties ai;

    public AgentRuntime(LanguageModel model, ToolRegistry tools, JarvisAiProperties props,
                        JarvisPersonaProperties persona) {
        this.model = model;
        this.tools = tools;
        this.maxSteps = props.getMaxSteps();
        this.persona = persona;
        this.ai = props;
    }

    public AgentRun run(AgentDefinition agent, String userMessage, String context) {
        return run(agent, userMessage, context, List.of(), null);
    }

    public AgentRun run(AgentDefinition agent, String userMessage, String context, List<ChatMessage> history) {
        return run(agent, userMessage, context, history, null);
    }

    /**
     * Runs the tool-calling loop, emitting each {@link Step} to {@code onStep} as
     * it happens (for live streaming). {@code onStep} may be {@code null}.
     */
    public AgentRun run(AgentDefinition agent, String userMessage, String context,
                        List<ChatMessage> history, java.util.function.Consumer<Step> onStep) {
        List<ChatMessage> messages = new ArrayList<>();
        String system = agent.systemPrompt();
        if (persona.isEnabled() && persona.getPrompt() != null && !persona.getPrompt().isBlank()) {
            // Global JARVIS persona layered ahead of the agent's task-specific prompt.
            system = persona.getPrompt().strip() + "\n\n" + system;
        }
        if (context != null && !context.isBlank()) {
            system += "\n\nContext you may use:\n" + context;
        }
        messages.add(ChatMessage.system(system));
        if (history != null) {
            messages.addAll(history); // prior conversation turns for continuity
        }
        messages.add(ChatMessage.user(userMessage));

        List<ToolSpec> toolSpecs = tools.specsFor(agent.toolNames());
        List<Step> steps = new ArrayList<>();
        int promptTokens = 0;
        int completionTokens = 0;
        // Whether this agent is even capable of a real action, and whether one actually SUCCEEDED.
        // Used by the honesty guard to detect an answer that claims it did something it never did.
        boolean agentCanMutate = agent.toolNames().stream()
                .map(tools::find).flatMap(Optional::stream).anyMatch(Tool::mutates);
        boolean toolCalled = false;
        boolean mutationSucceeded = false;

        for (int i = 0; i < maxSteps; i++) {
            ModelResponse resp = model.generate(messages, toolSpecs);
            promptTokens += resp.promptTokens();
            completionTokens += resp.completionTokens();

            if (resp.wantsTools()) {
                // Record the model's tool-use turn structurally so it round-trips to the provider.
                messages.add(ChatMessage.assistantToolCalls(resp.toolCalls()));
                for (ToolCall call : resp.toolCalls()) {
                    Tool tool = tools.find(call.name()).orElse(null);
                    String result = tool == null
                            ? "Unknown tool: " + call.name()
                            : tool.execute(call.argumentsJson());
                    toolCalled = true;
                    if (tool != null && tool.mutates() && !isFailure(result)) {
                        mutationSucceeded = true;
                    }
                    Step step = new Step("tool", "Called " + call.name(), truncate(result));
                    steps.add(step);
                    emit(onStep, step);
                    messages.add(ChatMessage.tool(result, call.id()));
                }
                continue;
            }

            // Honesty guard: an action-capable agent that used tools but landed NO successful action,
            // yet whose reply claims it completed something — correct the reply to match reality.
            String text = resp.text();
            if (agentCanMutate && toolCalled && !mutationSucceeded && claimsCompletedAction(text)) {
                text = correctOverclaim(text, steps);
            }
            Step answer = new Step("answer", "Composed the answer", null);
            steps.add(answer);
            emit(onStep, answer);
            return new AgentRun(text, steps, promptTokens, completionTokens, model.name());
        }
        return new AgentRun("I couldn't complete this within the step budget.", steps,
                promptTokens, completionTokens, model.name());
    }

    /** A tool result that signals the action did NOT happen (so it doesn't count as a success). */
    private static boolean isFailure(String result) {
        if (result == null || result.isBlank()) {
            return true;
        }
        String r = result.strip().toLowerCase();
        return r.startsWith("error") || r.startsWith("unknown tool") || r.startsWith("could not")
                || r.startsWith("couldn't") || r.startsWith("failed") || r.startsWith("no ")
                || r.contains("not a file") || r.contains("does not exist") || r.contains("permission denied");
    }

    /** Honest banner prepended when an action was claimed but the tools confirm none succeeded. */
    private static final String NOT_DONE =
            "⚠️ I didn't actually complete that — no action succeeded this turn (the tools I ran didn't make "
            + "the change), so nothing was created or saved. Treat the details below as a plan, not a result.";

    /**
     * Generic detector that the reply ASSERTS a finished action (any domain — files, email, memory, …),
     * so it can be cross-checked against what actually happened. Plain English completion language, not a
     * per-task rule.
     */
    private static boolean claimsCompletedAction(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase();
        String[] claims = {"i've created", "i have created", "i created", "i've built", "i built",
                "i've added", "i added", "i've saved", "i saved", "i've written", "i wrote", "i've set up",
                "i set up", "i've generated", "i generated", "i've updated", "i updated", "i've sent",
                "i sent", "i've deleted", "i deleted", "i've stored", "i stored", "successfully created",
                "successfully saved", "successfully built", "the project is located", "the file is located",
                "is now saved", "has been created", "have been created", "has been saved"};
        for (String c : claims) {
            if (t.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Corrects an over-claim. The DETECTION is agentic/general (we know from tool evidence that no action
     * succeeded). For the WORDING we first try the model — give it the reply + real tool results and ask
     * for an honest rewrite — but only accept that rewrite if it's well-formed; a weak local model often
     * echoes the prompt or dumps code, so on any malformed/empty/oversized output we fall back to a
     * deterministic honest banner. Either way the user is never told a fabricated success.
     */
    private String correctOverclaim(String answer, List<Step> steps) {
        String rewrite = realModel() ? modelRewrite(answer, steps) : null;
        if (isCleanRewrite(rewrite, answer)) {
            return rewrite;
        }
        return NOT_DONE + "\n\n" + (answer == null ? "" : answer);
    }

    private String modelRewrite(String answer, List<Step> steps) {
        try {
            String evidence = steps.stream()
                    .filter(s -> "tool".equals(s.kind()))
                    .map(s -> "- " + s.label() + (s.detail() == null ? "" : " -> " + s.detail()))
                    .collect(Collectors.joining("\n"));
            String system = "Rewrite the assistant REPLY so it is HONEST about what actually happened, using the "
                    + "real TOOL RESULTS. The reply claimed it completed an action, but the tool results show no "
                    + "such action succeeded. State plainly that it did not complete and give the next step. Output "
                    + "ONLY the rewritten reply as plain prose — no code, no headings, no labels.";
            String user = "REPLY:\n" + (answer == null ? "" : answer)
                    + "\n\nTOOL RESULTS THIS TURN:\n" + (evidence.isBlank() ? "(no tools succeeded)" : evidence);
            ModelResponse r = model.generate(
                    List.of(ChatMessage.system(system), ChatMessage.user(user)), List.of(), cheapModel());
            return r == null || r.text() == null ? null : r.text().strip();
        } catch (RuntimeException e) {
            return null;   // never let the guard break a turn
        }
    }

    /** Accept a model rewrite only if it's clean prose (weak models echo the scaffold or dump code). */
    private static boolean isCleanRewrite(String rewrite, String original) {
        if (rewrite == null || rewrite.isBlank() || rewrite.equalsIgnoreCase("OK")) {
            return false;
        }
        String low = rewrite.toLowerCase();
        if (rewrite.contains("```") || low.contains("reply:") || low.contains("tool results")) {
            return false;   // echoed the prompt / dumped code
        }
        int cap = (original == null ? 0 : original.length()) + 400;
        return rewrite.length() <= cap;   // a correction shouldn't balloon
    }

    /** A real reasoning model is active (so the self-check is meaningful) — not the offline mock. */
    private boolean realModel() {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return p.equals("ollama")
                || ((p.equals("claude") || p.equals("anthropic")) && notBlank(ai.getAnthropicApiKey()))
                || (p.equals("openai") && notBlank(ai.getOpenaiApiKey()));
    }

    /** Cheap model for the meta self-check (same tier as the planner); null ⇒ provider default. */
    private String cheapModel() {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return switch (p) {
            case "claude", "anthropic" -> ai.getPlannerModelClaude();
            case "openai" -> ai.getPlannerModelOpenai();
            default -> null;
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void emit(java.util.function.Consumer<Step> onStep, Step step) {
        if (onStep != null) {
            onStep.accept(step);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
