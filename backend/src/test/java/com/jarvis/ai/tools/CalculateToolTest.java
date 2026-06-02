package com.jarvis.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class CalculateToolTest {

    private final CalculateTool tool = new CalculateTool(new ObjectMapper());

    private String calc(String expr) {
        return tool.execute("{\"expression\":\"" + expr + "\"}");
    }

    @Test
    void basicArithmeticIsExact() {
        assertThat(calc("3*3")).isEqualTo("3*3 = 9");
        assertThat(calc("1234*5678")).isEqualTo("1234*5678 = 7006652");
        assertThat(calc("(2+3)^4 / 5")).isEqualTo("(2+3)^4 / 5 = 125");
        assertThat(calc("10 % 3")).isEqualTo("10 % 3 = 1");
        assertThat(calc("-5 + 2")).isEqualTo("-5 + 2 = -3");
    }

    @Test
    void functionsAndConstants() {
        assertThat(calc("sqrt(144)")).isEqualTo("sqrt(144) = 12");
        assertThat(calc("abs(-7)")).isEqualTo("abs(-7) = 7");
        assertThat(calc("round(2 * pi)")).isEqualTo("round(2 * pi) = 6");
    }

    @Test
    void decimalResultsAndPrecedence() {
        assertThat(calc("1 + 2 * 3")).isEqualTo("1 + 2 * 3 = 7");      // precedence
        assertThat(calc("7 / 2")).isEqualTo("7 / 2 = 3.5");
    }

    @Test
    void rejectsGarbageGracefully() {
        assertThat(calc("3 +")).contains("Couldn't evaluate");
        assertThat(calc("rm -rf /")).contains("Couldn't evaluate");   // not code, just a bad expression
    }
}
