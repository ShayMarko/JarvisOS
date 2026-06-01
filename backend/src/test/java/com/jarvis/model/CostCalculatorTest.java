package com.jarvis.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CostCalculatorTest {

    @Test
    void computesCostFromTokenUsage() {
        ModelDescriptor opus = new ModelDescriptor("opus", "anthropic", false, 0.015, 0.075, 5, 1500, true);
        // 1k input + 1k output = 0.015 + 0.075
        assertThat(CostCalculator.cost(opus, 1000, 1000)).isEqualTo(0.09);
    }

    @Test
    void localModelIsFree() {
        ModelDescriptor local = new ModelDescriptor("mock-local", "mock", true, 0, 0, 2, 5, true);
        assertThat(CostCalculator.cost(local, 5000, 5000)).isZero();
    }
}
