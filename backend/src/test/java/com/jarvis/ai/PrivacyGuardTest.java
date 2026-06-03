package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PrivacyGuardTest {

    /** A stub model whose verdict (YES/NO) the semantic check will read. */
    private PrivacyGuard guard(String verdict, boolean enabled) {
        JarvisAiProperties ai = new JarvisAiProperties();
        ai.setPrivacyGuard(enabled);
        LanguageModel m = new LanguageModel() {
            @Override public ModelResponse generate(List<ChatMessage> messages, List<ToolSpec> tools) {
                return ModelResponse.text(verdict, 1, 1);
            }
            @Override public String name() { return "stub-local"; }
        };
        return new PrivacyGuard(ai, m);
    }

    @Test
    void catchesSecretsDeterministically_evenIfTheClassifierSaysNo() {
        PrivacyGuard g = guard("NO", true);   // the secret patterns must win regardless of the model
        assertThat(g.keepLocal("my key is sk-abc123DEF456ghi789jkl")).isTrue();
        assertThat(g.keepLocal("use AKIA1234567890ABCDEF for aws")).isTrue();
        assertThat(g.keepLocal("db password: hunter2longenough")).isTrue();
    }

    @Test
    void usesTheLocalSemanticVerdictForNonSecrets() {
        assertThat(guard("YES", true).keepLocal("here are my salary and bank account details")).isTrue();
        assertThat(guard("NO", true).keepLocal("what is the capital of France?")).isFalse();
    }

    @Test
    void disabledGuardNeverKeepsLocal() {
        assertThat(guard("YES", false).keepLocal("my key is sk-abc123DEF456ghi789jkl")).isFalse();
    }
}
