package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.mcp.McpManager;

import lombok.RequiredArgsConstructor;

/** Invokes a tool on a connected MCP server (use mcp_list first to see what's available). */
@Component
@RequiredArgsConstructor
public class McpCallTool implements Tool {

    private final McpManager mcp;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("mcp_call",
                "Call a tool on a connected MCP server. Use mcp_list first to discover server and tool names.",
                "{\"type\":\"object\",\"properties\":{"
                + "\"server\":{\"type\":\"string\",\"description\":\"MCP server name\"},"
                + "\"tool\":{\"type\":\"string\",\"description\":\"tool name on that server\"},"
                + "\"arguments\":{\"type\":\"object\",\"description\":\"tool arguments object\"}},"
                + "\"required\":[\"server\",\"tool\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        try {
            var root = mapper.readTree(args == null || args.isBlank() ? "{}" : args);
            String server = root.path("server").asText("");
            String tool = root.path("tool").asText("");
            if (server.isBlank() || tool.isBlank()) {
                return "Provide both 'server' and 'tool' (call mcp_list to see options).";
            }
            String argumentsJson = root.has("arguments") ? root.path("arguments").toString() : "{}";
            return mcp.call(server, tool, argumentsJson);
        } catch (Exception e) {
            return "Error calling MCP tool: " + e.getMessage();
        }
    }
}
