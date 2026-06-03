package com.jarvis.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.jarvis.ai.ToolCall;

/** The trace labels for bridge tools name the specific service (enrichment #2). */
class AgentRuntimeLabelTest {

    @Test
    void namesTheConnectorAndAction() {
        ToolCall c = new ToolCall("1", "connector_invoke", "{\"connector\":\"github\",\"action\":\"get_pr\"}");
        assertThat(AgentRuntime.toolLabel(c)).isEqualTo("Connector · github.get_pr");
    }

    @Test
    void namesTheMcpServerAndTool() {
        ToolCall c = new ToolCall("1", "mcp_call", "{\"server\":\"filesystem\",\"tool\":\"read_file\"}");
        assertThat(AgentRuntime.toolLabel(c)).isEqualTo("MCP · filesystem.read_file");
    }

    @Test
    void fallsBackToGenericForOrdinaryTools() {
        assertThat(AgentRuntime.toolLabel(new ToolCall("1", "kb_search", "{\"query\":\"x\"}")))
                .isEqualTo("Called kb_search");
    }

    @Test
    void fallsBackWhenArgsAreMissing() {
        assertThat(AgentRuntime.toolLabel(new ToolCall("1", "connector_invoke", "")))
                .isEqualTo("Called connector_invoke");
    }
}
