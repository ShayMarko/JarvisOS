package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.connectors.ConnectorRegistry;

/**
 * Bridges connectors into the agent tool loop (spec §9): one tool lets an agent
 * invoke any connector action, so the Brain can reach external services through
 * the same mechanism as local capabilities.
 */
@Component
public class ConnectorTool implements Tool {

    private final ConnectorRegistry registry;
    private final ObjectMapper mapper;

    public ConnectorTool(ConnectorRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec("connector_invoke",
                "Invoke an external connector action (Gmail, GitHub, Calendar, Slack, …).",
                "{\"type\":\"object\",\"properties\":{\"connector\":{\"type\":\"string\"},"
                + "\"action\":{\"type\":\"string\"},\"args\":{\"type\":\"object\"}},"
                + "\"required\":[\"connector\",\"action\"]}");
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            var node = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String connector = node.path("connector").asText("");
            String action = node.path("action").asText("");
            String args = node.has("args") ? node.get("args").toString() : "{}";
            return registry.invoke(connector, action, args);
        } catch (Exception e) {
            return "Connector error: " + e.getMessage();
        }
    }
}
