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
import com.jarvis.model.ModelDescriptor;

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
        return run(agent, userMessage, context, history, onStep, null);
    }

    /**
     * As above, but runs on a SPECIFIC model {@code chosen} per task by the Model Router (provider +
     * model id). When {@code chosen} is {@code null}, uses the configured default provider.
     */
    public AgentRun run(AgentDefinition agent, String userMessage, String context,
                        List<ChatMessage> history, java.util.function.Consumer<Step> onStep,
                        ModelDescriptor chosen) {
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
        boolean wroteFile = false;   // a write_file actually succeeded this run (drives the build-continuation loop)
        boolean reflected = false;   // capability-reflection retry already used this run (only once)
        int continuations = 0;       // build-continuation nudges used (bounded)
        // The model actually used this run (the per-task pick, or the provider default) — for traces.
        String usedModel = chosen != null ? chosen.id() : model.name();

        for (int i = 0; i < maxSteps; i++) {
            ModelResponse resp = chosen == null
                    ? model.generate(messages, toolSpecs)
                    : model.generateOn(chosen.provider(), chosen.id(), messages, toolSpecs);
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
                        if ("write_file".equals(call.name())) {
                            wroteFile = true;
                        }
                    }
                    Step step = new Step("tool", "Called " + call.name(), truncate(result));
                    steps.add(step);
                    emit(onStep, step);
                    messages.add(ChatMessage.tool(result, call.id()));
                }
                continue;
            }

            String text = resp.text();

            // Capability self-discovery: if the model is about to REFUSE but it actually HAS tools, hand
            // it its own tool list once and let it reconsider — turning "I can't get the weather" into a
            // web_search call. General (any refusal, any tool), no per-request hard-coding.
            if (!reflected && !toolSpecs.isEmpty() && realModel() && isCapabilityRefusal(text)) {
                reflected = true;
                messages.add(ChatMessage.user(reflectionNudge(toolSpecs)));
                Step recheck = new Step("intent", "Re-checking my own capabilities", null);
                steps.add(recheck);
                emit(onStep, recheck);
                continue;   // give the model another pass, now aware of what it can do
            }

            // Build-continuation loop: the model is dumping code into chat instead of delivering files
            // via write_file. Two cases: (a) it already wrote a file then narrated the rest, or (b) a
            // build-capable agent narrated a whole multi-FILE project as several code blocks and wrote
            // nothing. Either way, push it to keep writing. A single snippet (one code block, no file
            // written) is left alone, so "show me a one-liner" answers aren't hijacked. Bounded.
            boolean dumpingProject = wroteFile || multipleCodeBlocks(text);
            if (dumpingProject && continuations < MAX_BUILD_CONTINUATIONS && realModel()
                    && agentCanMutate && hasCodeFence(text)) {
                continuations++;
                messages.add(ChatMessage.user(continuationNudge()));
                Step cont = new Step("intent", "Writing the remaining files", null);
                steps.add(cont);
                emit(onStep, cont);
                continue;
            }

            // Headless guarantee (this Mac mini is screenless, in a wall): a build reply must NEVER show
            // code or a leaked tool-call JSON in chat. Two nets for a build-capable agent:
            //  1) a leaked/garbled tool-call JSON as the "answer" → replace with an honest retry message;
            //  2) code blocks that survived the continuation loop → strip (the code is in the files).
            // A lone snippet ("show me a one-liner", which writes no file) is left intact.
            if (agentCanMutate && looksLikeLeakedToolCall(text)) {
                text = "I tried to build that but couldn't complete the file actions cleanly this time. "
                        + "Please ask me to try again.";
            } else if (agentCanMutate && (wroteFile || multipleCodeBlocks(text)) && hasCodeFence(text)) {
                text = stripCodeBlocks(text);
            }

            // Honesty guard: an action-capable agent that used tools but landed NO successful action,
            // yet whose reply claims it completed something — correct the reply to match reality.
            if (agentCanMutate && toolCalled && !mutationSucceeded && claimsCompletedAction(text)) {
                text = correctOverclaim(text, steps);
            }
            Step answer = new Step("answer", "Composed the answer", null);
            steps.add(answer);
            emit(onStep, answer);
            return new AgentRun(text, steps, promptTokens, completionTokens, usedModel);
        }
        return new AgentRun("I couldn't complete this within the step budget.", steps,
                promptTokens, completionTokens, usedModel);
    }

    /** Max times we push the model to keep writing files in one build (bounds the loop). */
    private static final int MAX_BUILD_CONTINUATIONS = 5;

    /** True if the reply contains a fenced code block — the signal that it's narrating code in chat. */
    private static boolean hasCodeFence(String text) {
        return text != null && text.contains("```");
    }

    /** True if the reply has 2+ fenced code blocks (≥4 ``` markers) — i.e. it narrated a multi-FILE
     *  project as code instead of writing the files. One block (a snippet) does NOT count. */
    private static boolean multipleCodeBlocks(String text) {
        if (text == null) {
            return false;
        }
        int fences = 0;
        for (int i = text.indexOf("```"); i >= 0; i = text.indexOf("```", i + 3)) {
            fences++;
        }
        return fences >= 4;
    }

    /** True if the reply is (or contains) a leaked tool-call JSON — has both a "name" and an
     *  "arguments"/"parameters" key. That's a botched tool call the user must never be shown. */
    private static boolean looksLikeLeakedToolCall(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("\"name\"") && (text.contains("\"arguments\"") || text.contains("\"parameters\""));
    }

    /** Remove fenced ``` … ``` code blocks from a build reply, leaving only the spoken-friendly prose.
     *  The code is already in the written files; the headless device must never show it in chat. */
    private static String stripCodeBlocks(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf("```", i);
            if (open < 0) {
                sb.append(text, i, text.length());
                break;
            }
            sb.append(text, i, open);
            int close = text.indexOf("```", open + 3);
            if (close < 0) {
                break;   // unterminated fence — drop the rest (it's code)
            }
            i = close + 3;
        }
        String prose = sb.toString().replaceAll("\\n{3,}", "\n\n").strip();
        if (prose.length() < 40) {
            return "I've written the project files to your Projects folder — open the Files explorer to "
                    + "view them. (Code isn't shown here; this machine runs headless.)";
        }
        return prose + "\n\n(Code was written to the project files, not shown here — this machine runs headless.)";
    }

    /** Push the model to keep delivering files via write_file rather than dumping them in the reply. */
    private static String continuationNudge() {
        return "You put code in your reply, but you run HEADLESS — code must be delivered with the write_file "
                + "tool, never shown in chat. For EVERY remaining file in this project, call write_file now: one "
                + "call per file, the full nested path under Projects/<app-name>/, and the COMPLETE file content. "
                + "Only once every file is written, reply with a short plain-language summary and NO code.";
    }

    /** Generic detector that the model is REFUSING on capability grounds (any domain), so it can be
     *  re-prompted with its own tools. Plain refusal language, not a per-task rule. */
    private static boolean isCapabilityRefusal(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase();
        String[] refusals = {"i can't", "i cannot", "i can not", "i'm unable", "i am unable",
                "i'm not able", "i am not able", "i don't have access", "i do not have access",
                "i don't have the ability", "i don't have the capability", "i'm not capable",
                "i don't have real-time", "i do not have real-time", "don't have real-time access",
                "cannot provide real-time", "can't provide real-time", "unable to access",
                "i'm sorry, but i can", "i am sorry, but i can", "i'm afraid i can"};
        for (String r : refusals) {
            if (t.contains(r)) {
                return true;
            }
        }
        return false;
    }

    /** The nudge that lists the agent's own tools and asks it to use the fitting one instead of refusing. */
    private static String reflectionNudge(List<ToolSpec> toolSpecs) {
        String names = toolSpecs.stream().map(ToolSpec::name).collect(Collectors.joining(", "));
        return "Before you refuse — you DO have tools available: " + names + ". Re-read my last request and, "
                + "if ANY of these can accomplish it, call that tool now instead of saying you can't "
                + "(e.g. use web_search to look up current or unknown facts like weather, prices, news or people; "
                + "calculate for math; read_file/search_files for my files). Only refuse if genuinely none apply.";
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
