package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryDraft;
import com.jarvis.memory.MemoryService;

import lombok.RequiredArgsConstructor;

/**
 * A/B self-testing — lets Jarvis run cheap experiments and remember them. {@code start} records two variants
 * + a hypothesis + the metric to judge by; {@code status} lists open experiments; {@code resolve} records the
 * winner (judged from real numbers, e.g. the Plausible connector) so the loser is dropped and the winner kept.
 * Backed by the Memory store (category {@code experiment}) so it persists and shows in the Memory Manager.
 */
@Component
@RequiredArgsConstructor
public class AbTestTool implements Tool {

    private static final String CATEGORY = "experiment";

    private final MemoryService memory;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("ab_test",
                "Run an A/B experiment: 'start' two variants for a product, 'status' to list open ones, 'resolve' to "
                + "record the winner from real metrics so the loser is dropped.",
                "{\"type\":\"object\",\"properties\":{"
                + "\"action\":{\"type\":\"string\",\"enum\":[\"start\",\"status\",\"resolve\"]},"
                + "\"product\":{\"type\":\"string\"},"
                + "\"hypothesis\":{\"type\":\"string\"},"
                + "\"variantA\":{\"type\":\"string\",\"description\":\"variant A label/url\"},"
                + "\"variantB\":{\"type\":\"string\",\"description\":\"variant B label/url\"},"
                + "\"metric\":{\"type\":\"string\",\"description\":\"what decides the winner (e.g. signups, conversion)\"},"
                + "\"winner\":{\"type\":\"string\",\"description\":\"for resolve: A or B + the numbers\"}},"
                + "\"required\":[\"action\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        try {
            String action = ToolArgs.str(mapper, args, "action").toLowerCase();
            return switch (action) {
                case "start" -> start(args);
                case "status" -> status();
                case "resolve" -> resolve(args);
                default -> "Provide 'action' = start | status | resolve.";
            };
        } catch (Exception e) {
            return "Error in ab_test: " + e.getMessage();
        }
    }

    private String start(String args) {
        String product = ToolArgs.str(mapper, args, "product");
        String a = ToolArgs.str(mapper, args, "variantA");
        String b = ToolArgs.str(mapper, args, "variantB");
        if (product.isBlank() || a.isBlank() || b.isBlank()) {
            return "To start: provide 'product', 'variantA' and 'variantB'.";
        }
        String metric = ToolArgs.str(mapper, args, "metric");
        String hypothesis = ToolArgs.str(mapper, args, "hypothesis");
        String content = "status=running\nA=" + a + "\nB=" + b
                + "\nmetric=" + (metric.isBlank() ? "(unset)" : metric)
                + "\nhypothesis=" + (hypothesis.isBlank() ? "(none)" : hypothesis);
        memory.create(new MemoryDraft(CATEGORY, "AB: " + product, content, "ab_test", 0.9, null, null, null, true));
        return "🧪 Experiment started for \"" + product + "\": A=" + a + " vs B=" + b
                + ". Run both, then ab_test resolve once you have the numbers.";
    }

    private String status() {
        List<Memory> open = memory.list("").stream()
                .filter(m -> CATEGORY.equalsIgnoreCase(m.getCategory()) && m.getContent() != null
                        && m.getContent().contains("status=running"))
                .toList();
        if (open.isEmpty()) {
            return "No open experiments.";
        }
        StringBuilder sb = new StringBuilder("Open experiments:\n");
        for (Memory m : open) {
            sb.append("• ").append(m.getTitle()).append('\n');
        }
        return sb.toString().strip();
    }

    private String resolve(String args) {
        String product = ToolArgs.str(mapper, args, "product");
        String winner = ToolArgs.str(mapper, args, "winner");
        if (product.isBlank() || winner.isBlank()) {
            return "To resolve: provide 'product' and the 'winner' (A or B + the numbers).";
        }
        Memory exp = memory.list("").stream()
                .filter(m -> CATEGORY.equalsIgnoreCase(m.getCategory())
                        && m.getTitle() != null && m.getTitle().equalsIgnoreCase("AB: " + product))
                .findFirst().orElse(null);
        if (exp == null) {
            return "No experiment found for \"" + product + "\". Start one first.";
        }
        String content = (exp.getContent() == null ? "" : exp.getContent())
                .replace("status=running", "status=decided") + "\nwinner=" + winner;
        memory.update(exp.getId(), new MemoryDraft(CATEGORY, exp.getTitle(), content,
                "ab_test", 0.9, null, null, null, true));
        return "✅ Resolved \"" + product + "\": winner=" + winner + ". Keep the winner, retire the other variant.";
    }
}
