package com.jarvis.agent;

import java.util.ArrayList;
import java.util.List;

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

    public AgentRuntime(LanguageModel model, ToolRegistry tools, JarvisAiProperties props,
                        JarvisPersonaProperties persona) {
        this.model = model;
        this.tools = tools;
        this.maxSteps = props.getMaxSteps();
        this.persona = persona;
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
                    Step step = new Step("tool", "Called " + call.name(), truncate(result));
                    steps.add(step);
                    emit(onStep, step);
                    messages.add(ChatMessage.tool(result, call.id()));
                }
                continue;
            }

            Step answer = new Step("answer", "Composed the answer", null);
            steps.add(answer);
            emit(onStep, answer);
            return new AgentRun(resp.text(), steps, promptTokens, completionTokens, model.name());
        }
        return new AgentRun("I couldn't complete this within the step budget.", steps,
                promptTokens, completionTokens, model.name());
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
