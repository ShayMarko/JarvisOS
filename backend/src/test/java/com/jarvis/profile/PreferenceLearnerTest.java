package com.jarvis.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.approval.ApprovalService;
import com.jarvis.security.RiskLevel;

class PreferenceLearnerTest {

    private LanguageModel model;
    private ProfileService profile;
    private ApprovalService approvals;
    private PreferenceLearner learner;

    @BeforeEach
    void setUp() {
        model = mock(LanguageModel.class);
        profile = mock(ProfileService.class);
        approvals = mock(ApprovalService.class);
        learner = new PreferenceLearner(model, new JarvisAiProperties(), profile, approvals,
                new JarvisPreferenceProperties());
        when(profile.read()).thenReturn("# About Me\n## What Jarvis has learned\n");
    }

    private void modelReturns(String text) {
        when(model.generateOn(eq("ollama"), anyString(), anyList(), anyList()))
                .thenReturn(ModelResponse.text(text, 1, 1));
    }

    @Test
    void offersToRememberANewPreference() {
        modelReturns("Prefers pnpm over npm.");
        Optional<String> proposed = learner.learnFrom("I use pnpm instead of npm for everything", "Got it.");
        assertThat(proposed).contains("Prefers pnpm over npm.");
        verify(approvals).submit(eq("profile:learn"), anyString(), anyString(),
                eq(RiskLevel.LOW), anyString(), any());
    }

    @Test
    void doesNotProposeFactsTheProfileAlreadyKnows() {
        when(profile.read()).thenReturn("# About Me\n## What Jarvis has learned\n- Prefers pnpm over npm.\n");
        modelReturns("Prefers pnpm over npm.");
        Optional<String> proposed = learner.learnFrom("I use pnpm instead of npm", "Sure.");
        assertThat(proposed).isEmpty();
        verify(approvals, never()).submit(anyString(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void skipsSmallTalkWithoutBurningAModelCall() {
        Optional<String> candidate = learner.extractCandidate("what's the weather today", "It's sunny.");
        assertThat(candidate).isEmpty();
        verify(model, never()).generateOn(anyString(), anyString(), anyList(), anyList());
    }

    @Test
    void treatsNoneAsNothingToLearn() {
        modelReturns("NONE");
        Optional<String> candidate = learner.extractCandidate("I use my computer a lot", "Okay.");
        assertThat(candidate).isEmpty();
    }
}
