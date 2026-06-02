package com.jarvis.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.agent.AgentDefinition;
import com.jarvis.agent.AgentSelector;
import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;

import lombok.RequiredArgsConstructor;

/**
 * Task decomposition (spec §6 "Planner / Task Decomposition"). Turns a request into
 * a DAG of sub-tasks, each routed to the best agent, with explicit dependencies.
 *
 * <p>Two tiers, both real and offline-safe:
 * <ul>
 *   <li><b>LLM planner</b> — for compound/complex requests, asks the model for a JSON
 *       plan ({@code steps[]} with {@code id}/{@code task}/{@code dependsOn}); the
 *       Orchestrator then runs independent steps in parallel and feeds each dependent
 *       step the outputs it waited on. This produces genuine pipelines
 *       (e.g. "research → draft → review").</li>
 *   <li><b>Heuristic planner</b> — the fallback: connective splitting ("then"/";"/…)
 *       into independent steps. Used for simple requests and whenever the model's plan
 *       is missing/invalid, so the system never depends on a particular model.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class Planner {

    private static final Logger log = LoggerFactory.getLogger(Planner.class);

    // Split on "and then" / "then" / ";" / newlines / numbered list markers.
    private static final String SPLIT = "(?i)\\s+and\\s+then\\s+|\\s+then\\s+|\\s*;\\s*|\\n+|\\s+\\d+\\.\\s+";

    /** Below this length and with no connectives, skip LLM planning (fast single-agent path). */
    private static final int COMPLEX_LENGTH = 140;
    private static final int MAX_STEPS = 6;

    private final AgentSelector selector;
    private final LanguageModel model;
    private final ObjectMapper mapper;
    private final JarvisAiProperties ai;

    public List<PlanStep> plan(String message) {
        String msg = message == null ? "" : message.strip();
        List<String> fragments = splitHeuristic(msg);

        // Fast path: a short single-intent request needs no decomposition and no LLM call.
        if (fragments.size() <= 1 && msg.length() < COMPLEX_LENGTH) {
            return List.of(singleStep(msg));
        }

        // Compound or complex: try a real LLM DAG plan; fall back to the heuristic split.
        List<PlanStep> llm = tryLlmPlan(msg);
        if (llm != null && llm.size() > 1) {
            return llm;
        }
        return heuristicPlan(msg, fragments);
    }

    // --- heuristic tier -----------------------------------------------------

    private List<String> splitHeuristic(String msg) {
        return Arrays.stream(msg.split(SPLIT)).map(String::strip).filter(s -> s.length() > 2).toList();
    }

    private List<PlanStep> heuristicPlan(String msg, List<String> fragments) {
        if (fragments.size() <= 1) {
            return List.of(singleStep(msg));
        }
        List<PlanStep> steps = new ArrayList<>();
        int i = 1;
        for (String fragment : fragments) {
            AgentDefinition a = selector.select(fragment);
            steps.add(new PlanStep("s" + (i++), a.slug(), a.name(), fragment));
        }
        return steps;
    }

    private PlanStep singleStep(String msg) {
        AgentDefinition a = selector.select(msg);
        return new PlanStep("s1", a.slug(), a.name(), msg);
    }

    // --- LLM tier -----------------------------------------------------------

    private List<PlanStep> tryLlmPlan(String msg) {
        try {
            String system = """
                You are a task planner. Break the user's request into the FEWEST independent or \
                dependent sub-tasks needed (max %d). Reply with ONLY a JSON object, no prose:
                {"steps":[{"id":"s1","task":"...","dependsOn":[]},{"id":"s2","task":"...","dependsOn":["s1"]}]}
                Rules: ids are s1,s2,...; dependsOn lists ids whose RESULTS this step needs; \
                keep tasks self-contained imperatives; if the request is really one task, return a single step.\
                """.formatted(MAX_STEPS);
            // The plan is tiny JSON — run it on a cheap model for paid providers.
            ModelResponse resp = model.generate(
                    List.of(ChatMessage.system(system), ChatMessage.user(msg)), List.of(), plannerModel());
            String text = resp == null ? null : resp.text();
            if (text == null || text.isBlank()) {
                return null;
            }
            return parsePlan(text);
        } catch (RuntimeException e) {
            log.debug("LLM planning failed, falling back to heuristic: {}", e.getMessage());
            return null;
        }
    }

    /** Cheap model for the planner on paid providers; null (= default model) for ollama/mock. */
    private String plannerModel() {
        String provider = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return switch (provider) {
            case "claude", "anthropic" -> ai.getPlannerModelClaude();
            case "openai" -> ai.getPlannerModelOpenai();
            default -> null;
        };
    }

    /** Parse + validate the model's JSON plan; route each step to an agent. Null if unusable. */
    List<PlanStep> parsePlan(String text) {
        String json = extractJsonObject(text);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode stepsNode = root.path("steps");
            if (!stepsNode.isArray() || stepsNode.isEmpty()) {
                return null;
            }
            List<PlanStep> raw = new ArrayList<>();
            Set<String> ids = new LinkedHashSet<>();
            int i = 1;
            for (JsonNode s : stepsNode) {
                String task = s.path("task").asText("").strip();
                if (task.isEmpty()) {
                    continue;
                }
                String id = s.path("id").asText("").strip();
                if (id.isEmpty() || !ids.add(id)) {
                    id = "s" + i;
                    ids.add(id);
                }
                List<String> deps = new ArrayList<>();
                for (JsonNode d : s.path("dependsOn")) {
                    deps.add(d.asText());
                }
                AgentDefinition a = selector.select(task);
                raw.add(new PlanStep(id, a.slug(), a.name(), task, deps));
                i++;
                if (raw.size() >= MAX_STEPS) {
                    break;
                }
            }
            if (raw.isEmpty()) {
                return null;
            }
            return sanitizeDependencies(raw);
        } catch (Exception e) {
            log.debug("Could not parse LLM plan JSON: {}", e.getMessage());
            return null;
        }
    }

    /** Keep only backward (acyclic) dependencies on known ids, so execution always terminates. */
    private List<PlanStep> sanitizeDependencies(List<PlanStep> steps) {
        Set<String> known = new LinkedHashSet<>();
        steps.forEach(s -> known.add(s.id()));
        List<PlanStep> out = new ArrayList<>();
        Set<String> seenSoFar = new LinkedHashSet<>();
        for (PlanStep s : steps) {
            List<String> deps = new ArrayList<>();
            for (String d : s.dependsOn()) {
                // keep only dependencies declared earlier ⇒ forward edges only ⇒ acyclic DAG
                if (known.contains(d) && seenSoFar.contains(d) && !d.equals(s.id())) {
                    deps.add(d);
                }
            }
            out.add(new PlanStep(s.id(), s.agentSlug(), s.agentName(), s.task(), List.copyOf(deps)));
            seenSoFar.add(s.id());
        }
        return out;
    }

    /** Pull the first balanced {...} block out of a model reply (tolerates code fences/prose). */
    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}
