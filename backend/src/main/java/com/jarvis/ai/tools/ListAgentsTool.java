package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.agent.AgentDefinition;
import com.jarvis.agent.AgentRegistry;
import com.jarvis.ai.ToolSpec;

import lombok.RequiredArgsConstructor;

/** Lists Jarvis's agent roster — so "what agents do I have / list my agents" works over Discord/voice/chat. */
@Component
@RequiredArgsConstructor
public class ListAgentsTool implements Tool {

    private final AgentRegistry agents;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_agents",
                "List Jarvis's agents (the roster): name, role and category. Use when asked things like "
                + "'what agents do I have', 'list my agents', 'which agents exist'.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String argumentsJson) {
        List<AgentDefinition> all = agents.all();
        if (all.isEmpty()) {
            return "No agents are registered.";
        }
        StringBuilder sb = new StringBuilder("🤖 Agents (" + all.size() + "):\n");
        for (AgentDefinition a : all) {
            String role = a.role() == null ? "" : a.role();
            if (role.length() > 80) {
                role = role.substring(0, 79) + "…";
            }
            sb.append("• ").append(a.name()).append(" — ").append(role)
              .append("  [").append(a.category()).append("]\n");
        }
        return sb.toString().strip();
    }
}
