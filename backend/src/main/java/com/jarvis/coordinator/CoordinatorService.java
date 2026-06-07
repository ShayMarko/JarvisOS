package com.jarvis.coordinator;

import com.jarvis.ai.ModelTier;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.notification.NotificationService;
import com.jarvis.revenue.Product;
import com.jarvis.revenue.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * The autonomous "CEO" loop — the capstone that makes Jarvis RUN the income funnel instead of waiting to
 * be asked. On a cron (and only when explicitly enabled) it reviews the north-star goal + the current
 * product portfolio, asks the model for the single highest-value next action (SCOUT / BUILD / MARKET /
 * ANALYZE / HOLD) with a concrete instruction, then dispatches that instruction through the Orchestrator
 * — which routes it to the right specialist agent (Scout, a builder lane, Growth, Analyst).
 *
 * <p>Brain-like by design: the model decides, deterministic code executes and bounds it. It stops short of
 * publishing/charging (selling + deploying remain the owner's call), runs at most {@code maxActionsPerRun}
 * actions per pass, only fires on a real model, and every paid call is already capped by the monthly USD budget.
 */
@Service
@RequiredArgsConstructor
public class CoordinatorService {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorService.class);

    private final JarvisCoordinatorProperties props;
    private final ProductService products;
    private final com.jarvis.brain.GoalService goals;
    @Lazy
    private final Orchestrator orchestrator;
    private final NotificationService notifications;
    private final LanguageModel model;
    private final JarvisAiProperties ai;

    @Scheduled(cron = "${jarvis.coordinator.cron:0 0 10 * * *}", zone = "${jarvis.briefing.zone:}")
    void scheduledTick() {
        if (!props.isEnabled()) {
            return;   // autonomy is opt-in
        }
        tick();
    }

    /**
     * Run one coordination pass: decide the next action and dispatch it. Returns a short human summary
     * (also used by the manual /api/coordinator/tick trigger). Safe to call when disabled — it just reports.
     */
    public String tick() {
        if (!ModelTier.isReal(ai)) {
            return "Coordinator idle — no real model configured (would only plan against the offline mock).";
        }
        try {
            Decision d = decideNextAction();
            if (d == null || d.action() == Action.HOLD) {
                String why = d == null ? "no clear signal" : d.instruction();
                notifications.notify("info", "Jarvis Coordinator", "Holding this cycle — " + why, "coordinator");
                return "HOLD — " + why;
            }
            int budget = Math.max(1, props.getMaxActionsPerRun());
            StringBuilder summary = new StringBuilder();
            // For now one decision drives one dispatch; the loop stays observable (maxActionsPerRun caps it).
            for (int i = 0; i < budget && i < 1; i++) {
                ChatResponse r = orchestrator.handle(d.instruction(), "coordinator");
                String answer = r == null || r.answer() == null ? "(no result)" : r.answer();
                String head = answer.length() > 280 ? answer.substring(0, 279) + "…" : answer;
                summary.append(d.action()).append(": ").append(d.instruction()).append("\n→ ").append(head);
            }
            notifications.notify("info", "Jarvis Coordinator — " + d.action(),
                    "Goal: " + goal() + "\n\n" + summary, "coordinator");
            return summary.toString();
        } catch (RuntimeException e) {
            log.debug("Coordinator pass skipped: {}", e.getMessage());
            return "Coordinator pass error: " + e.getMessage();
        }
    }

    /** Ask the model to choose the next funnel action against the goal + portfolio. */
    private Decision decideNextAction() {
        ModelResponse r = model.generate(List.of(
                ChatMessage.system("You are Jarvis's autonomous COORDINATOR running a small one-person digital "
                        + "product business. Given the GOAL and the current PORTFOLIO, choose the SINGLE highest-value "
                        + "next action and write a concrete instruction for the specialist agent who will do it. "
                        + "Choose exactly one ACTION from: SCOUT (find new product opportunities), BUILD (create a "
                        + "specific new product), MARKET (drive traffic/marketing assets for a LIVE product), ANALYZE "
                        + "(review portfolio + spend, recommend), HOLD (nothing worth doing now). Rules: prefer "
                        + "finishing/marketing what already exists over starting new things; do NOT instruct anyone to "
                        + "publish, deploy live, or charge money — those are the owner's manual steps. Reply with EXACTLY "
                        + "two lines:\nACTION: <one of the words above>\nINSTRUCTION: <one concrete sentence>"),
                ChatMessage.user("GOAL: " + goal() + "\n\nPORTFOLIO:\n" + portfolioSummary())),
                List.of(), ModelTier.cheapModelId(ai));
        return parseDecision(r == null ? null : r.text());
    }

    /** Parse the model's 'ACTION: x / INSTRUCTION: y' reply. Unknown/blank → HOLD (safe default). */
    static Decision parseDecision(String text) {
        if (text == null || text.isBlank()) {
            return new Decision(Action.HOLD, "no decision returned");
        }
        Action action = Action.HOLD;
        String instruction = "";
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            String upper = line.toUpperCase();
            if (upper.startsWith("ACTION:")) {
                String word = line.substring(line.indexOf(':') + 1).strip().toUpperCase();
                for (Action a : Action.values()) {
                    if (word.startsWith(a.name())) {
                        action = a;
                        break;
                    }
                }
            } else if (upper.startsWith("INSTRUCTION:")) {
                instruction = line.substring(line.indexOf(':') + 1).strip();
            }
        }
        if (action != Action.HOLD && instruction.isBlank()) {
            return new Decision(Action.HOLD, "no instruction given");
        }
        return new Decision(action, instruction.isBlank() ? "nothing actionable" : instruction);
    }

    private String portfolioSummary() {
        List<Product> all = products.portfolio();
        if (all.isEmpty()) {
            return "(empty — nothing built yet; a SCOUT or BUILD is likely the right first move)";
        }
        StringBuilder sb = new StringBuilder();
        for (Product p : all) {
            sb.append("- ").append(p.getName()).append(" [").append(p.getType()).append("] status=")
                    .append(p.getStatus());
            if (p.getPriceUsd() != null) {
                sb.append(" price=$").append(p.getPriceUsd());
            }
            sb.append(" revenue=$").append(p.getRevenueUsd());
            if (p.getListingUrl() != null && !p.getListingUrl().isBlank()) {
                sb.append(" listed");
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** The persistent north-star if one's been set, else the configured default goal. */
    public String goal() {
        String g = goals.current();
        return (g != null && !g.isBlank()) ? g : props.getGoal();
    }

    public int portfolioSize() {
        return products.portfolio().size();
    }




    enum Action { SCOUT, BUILD, MARKET, ANALYZE, HOLD }

    record Decision(Action action, String instruction) {}
}
