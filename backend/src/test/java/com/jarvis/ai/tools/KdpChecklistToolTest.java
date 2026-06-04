package com.jarvis.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class KdpChecklistToolTest {

    private final KdpChecklistTool tool = new KdpChecklistTool(new ObjectMapper());

    @Test
    void returnsTheCanonicalChecklist() {
        String out = tool.execute("{}");
        assertThat(out).contains("KDP readiness").contains("Table of contents")
                .contains("AI-content disclosure").contains("keywords");
    }

    @Test
    void labelsTheBookWhenProvided() {
        assertThat(tool.execute("{\"book\":\"My Guide\"}")).contains("For: My Guide");
    }
}
