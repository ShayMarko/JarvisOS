package com.jarvis.ai;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * The Privacy Router's brain. Decides whether a request should be kept on the LOCAL model rather than
 * sent to a cloud provider — enforcing the user's rule that sensitive personal/financial/credential/
 * confidential information must not leave the machine unless truly necessary.
 *
 * <p>Two layers, both private (nothing here is sent to a cloud provider):
 * <ol>
 *   <li><b>Reliable secret scan</b> — high-precision patterns (API keys, tokens, private keys, card
 *       numbers, password=… lines). These are unambiguous secrets → always keep local.</li>
 *   <li><b>Smart semantic check</b> — asks the LOCAL model a yes/no: does this contain sensitive info
 *       that shouldn't go to a third-party AI? Runs on-device, so the content never leaves the box.</li>
 * </ol>
 * Fails open for NON-secret content if the local classifier is unavailable (the reliable secret scan
 * still applies), so cloud usage isn't blocked by a missing local model.
 */
@Component
@RequiredArgsConstructor
public class PrivacyGuard {

    private static final Logger log = LoggerFactory.getLogger(PrivacyGuard.class);

    /** Unambiguous secrets — industry-standard high-precision patterns. A hit always keeps it local. */
    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("sk-[A-Za-z0-9]{16,}"),                       // OpenAI-style key
            Pattern.compile("AKIA[0-9A-Z]{16}"),                          // AWS access key id
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{20,}"),                // GitHub token
            Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}"),              // Slack token
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),        // PEM private key
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"), // JWT
            Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b"),                 // card-like number run
            Pattern.compile("(?i)\\b(password|passwd|secret|api[_-]?key|access[_-]?token|private[_-]?key)\\b\\s*[:=]\\s*\\S+"));

    private final JarvisAiProperties ai;
    private final LanguageModel model;

    /** True if this text should be processed on the LOCAL model rather than a cloud provider. */
    public boolean keepLocal(String text) {
        if (text == null || text.isBlank() || !ai.isPrivacyGuard()) {
            return false;
        }
        for (Pattern p : SECRETS) {
            if (p.matcher(text).find()) {
                return true;   // an unambiguous secret — never send to cloud
            }
        }
        return classifySensitive(text);
    }

    /** Local-only semantic judgment — the content is classified on-device and never leaves the machine. */
    private boolean classifySensitive(String text) {
        try {
            String system = "You are a privacy classifier. Decide whether the user's message contains sensitive "
                    + "personal, financial, credential, medical, legal, or confidential work information that should "
                    + "NOT be sent to a third-party cloud AI. Reply with ONLY one word: YES or NO.";
            // Force the LOCAL model (ollama); the offline mock is the worst case — still local, never cloud.
            ModelResponse r = model.generateOn("ollama", ai.getOllamaModel(),
                    List.of(ChatMessage.system(system), ChatMessage.user(text)), List.of());
            String a = r == null || r.text() == null ? "" : r.text().trim().toLowerCase();
            return a.startsWith("yes");
        } catch (RuntimeException e) {
            // Hard secrets are already caught above; don't block cloud for ordinary content if the local
            // classifier isn't reachable.
            log.debug("Privacy classification unavailable ({}); allowing cloud for non-secret content.", e.getMessage());
            return false;
        }
    }
}
