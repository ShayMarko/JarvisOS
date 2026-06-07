package com.jarvis.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.jarvis.coordinator.CoordinatorService.Action;
import com.jarvis.coordinator.CoordinatorService.Decision;

class CoordinatorServiceTest {

    @Test
    void parsesActionAndInstruction() {
        Decision d = CoordinatorService.parseDecision("ACTION: MARKET\nINSTRUCTION: Draft launch posts for the CSV Toolkit.");
        assertThat(d.action()).isEqualTo(Action.MARKET);
        assertThat(d.instruction()).isEqualTo("Draft launch posts for the CSV Toolkit.");
    }

    @Test
    void toleratesNoiseAndExtraWordsAfterAction() {
        Decision d = CoordinatorService.parseDecision("Here is my plan.\nACTION: BUILD a thing\nINSTRUCTION: Build a tiny markdown-to-PDF API.");
        assertThat(d.action()).isEqualTo(Action.BUILD);
        assertThat(d.instruction()).isEqualTo("Build a tiny markdown-to-PDF API.");
    }

    @Test
    void blankOrUnparseableDefaultsToHold() {
        assertThat(CoordinatorService.parseDecision(null).action()).isEqualTo(Action.HOLD);
        assertThat(CoordinatorService.parseDecision("  ").action()).isEqualTo(Action.HOLD);
        assertThat(CoordinatorService.parseDecision("I'm not sure what to do.").action()).isEqualTo(Action.HOLD);
    }

    @Test
    void actionWithoutInstructionFallsToHold() {
        Decision d = CoordinatorService.parseDecision("ACTION: SCOUT\nINSTRUCTION:");
        assertThat(d.action()).isEqualTo(Action.HOLD);
    }
}
