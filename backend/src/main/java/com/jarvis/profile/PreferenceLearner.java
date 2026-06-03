package com.jarvis.profile;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.approval.ApprovalService;
import com.jarvis.security.RiskLevel;

import lombok.RequiredArgsConstructor;

/**
 * The preference-learning loop. After a conversation turn, it quietly asks the LOCAL model whether the
 * exchange revealed a durable fact/preference about the user worth remembering — and if so, OFFERS to
 * add it to the About-Me profile via the Approval Center. Two hard rules drive the design:
 *
 * <ul>
 *   <li><b>Consent.</b> It never edits the profile itself — it parks an approval whose deferred action
 *       is {@code profile.appendLearned(fact)}. Nothing is remembered unless the user says yes.</li>
 *   <li><b>Privacy.</b> The "is this worth remembering?" judgment runs on the LOCAL model, so personal
 *       content isn't sent to a cloud AI just to decide what to keep.</li>
 * </ul>
 *
 * It's smart, not a robot: a cheap hint pre-filter means it only spends a model call when the message
 * actually sounds like a preference, and it de-dupes against what the profile already knows.
 */
@Service
@RequiredArgsConstructor
public class PreferenceLearner {

    private static final Logger log = LoggerFactory.getLogger(PreferenceLearner.class);
    private static final int MAX_FACT_CHARS = 160;

    /** Daemon single-thread pool — the noticing happens off the chat path so replies never wait on it. */
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "preference-learner");
        t.setDaemon(true);
        return t;
    });

    private final LanguageModel model;
    private final JarvisAiProperties ai;
    private final ProfileService profile;
    private final ApprovalService approvals;
    private final JarvisPreferenceProperties props;

    /** Fire-and-forget: never blocks or breaks the turn that triggered it. */
    public void observe(String userMessage, String assistantReply) {
        if (!props.isEnabled() || userMessage == null || userMessage.length() < props.getMinMessageChars()) {
            return;
        }
        exec.submit(() -> {
            try {
                learnFrom(userMessage, assistantReply);
            } catch (RuntimeException e) {
                log.debug("preference learning skipped: {}", e.getMessage());
            }
        });
    }

    /**
     * Synchronous core (so a test can drive it): extract a candidate fact, and if it's genuinely new,
     * propose saving it. Returns the proposed fact, or empty if nothing was offered.
     */
    Optional<String> learnFrom(String userMessage, String assistantReply) {
        Optional<String> candidate = extractCandidate(userMessage, assistantReply);
        if (candidate.isEmpty() || !isNew(candidate.get())) {
            return Optional.empty();
        }
        propose(candidate.get());
        return candidate;
    }

    /** Local-only: decide whether the exchange reveals one durable, profile-worthy fact about the user. */
    Optional<String> extractCandidate(String userMessage, String assistantReply) {
        if (!mentionsPreference(userMessage)) {
            return Optional.empty();   // cheap pre-filter — don't burn a model call on small talk
        }
        try {
            String system = "You watch a conversation and capture ONE durable fact or preference about the USER "
                    + "worth remembering long-term in their profile (their identity, the tools/apps they use, how they "
                    + "like to be addressed, habits, standing do/don't rules). Ignore one-off task details and anything "
                    + "transient. Reply with JUST that one fact as a short third-person sentence (e.g. 'Prefers pnpm over "
                    + "npm.'). If there is nothing durable worth saving, reply with exactly NONE.";
            String convo = "User said: " + userMessage
                    + (assistantReply == null || assistantReply.isBlank() ? "" : "\nAssistant replied: " + assistantReply);
            ModelResponse r = model.generateOn("ollama", ai.getOllamaModel(),
                    List.of(ChatMessage.system(system), ChatMessage.user(convo)), List.of());
            String fact = r == null || r.text() == null ? "" : r.text().strip();
            if (fact.isBlank() || fact.equalsIgnoreCase("none") || fact.toLowerCase().startsWith("none")) {
                return Optional.empty();
            }
            if (fact.length() > MAX_FACT_CHARS) {
                fact = fact.substring(0, MAX_FACT_CHARS).strip() + "…";
            }
            return Optional.of(fact);
        } catch (RuntimeException e) {
            log.debug("preference extraction unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** True if the profile doesn't already capture this fact (significant-word overlap). */
    boolean isNew(String fact) {
        Set<String> factTerms = terms(fact);
        if (factTerms.isEmpty()) {
            return false;
        }
        String profileText = profile.read().toLowerCase();
        long present = factTerms.stream().filter(profileText::contains).count();
        // If the profile already mentions most of the fact's key words, treat it as known.
        return (double) present / factTerms.size() < 0.6;
    }

    private void propose(String fact) {
        approvals.submit("profile:learn",
                "Remember about you: " + (fact.length() > 60 ? fact.substring(0, 60) + "…" : fact),
                "Jarvis noticed this from your conversation and can add it to your About-Me profile. "
                        + "Approve to remember it; deny to forget it.",
                RiskLevel.LOW, fact, () -> profile.appendLearned(fact));
    }

    private boolean mentionsPreference(String message) {
        String m = message.toLowerCase();
        return props.getHints().stream().anyMatch(m::contains);
    }

    private static Set<String> terms(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() > 3)
                .collect(Collectors.toSet());
    }
}
