package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.connectors.ConnectorInfo;
import com.jarvis.connectors.ConnectorRegistry;

import lombok.RequiredArgsConstructor;

/** Lists Jarvis's connectors + status — so "what connectors / integrations do I have" works anywhere. */
@Component
@RequiredArgsConstructor
public class ListConnectorsTool implements Tool {

    private final ConnectorRegistry connectors;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_connectors",
                "List Jarvis's connectors/integrations: name, category, connection status, and which are set up. "
                + "Use when asked 'what connectors/integrations do I have', 'is GitHub connected', etc.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String argumentsJson) {
        List<ConnectorInfo> all = connectors.list();
        if (all.isEmpty()) {
            return "No connectors are registered.";
        }
        long connected = all.stream().filter(c -> "CONNECTED".equals(String.valueOf(c.status()))).count();
        StringBuilder sb = new StringBuilder("🔌 Connectors (" + connected + "/" + all.size() + " connected):\n");
        for (ConnectorInfo c : all) {
            sb.append("• ").append(c.name()).append(" [").append(c.category()).append("] — ")
              .append(String.valueOf(c.status()).toLowerCase());
            if (c.requiredSecret() != null && !c.requiredSecret().isBlank()) {
                sb.append(" (needs ").append(c.requiredSecret()).append(")");
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }
}
