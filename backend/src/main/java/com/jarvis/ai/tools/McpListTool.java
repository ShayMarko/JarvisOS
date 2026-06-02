package com.jarvis.ai.tools;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.mcp.McpClient.McpTool;
import com.jarvis.mcp.McpManager;

import lombok.RequiredArgsConstructor;

/** Lists the tools available from connected MCP servers. */
@Component
@RequiredArgsConstructor
public class McpListTool implements Tool {

    private final McpManager mcp;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("mcp_list",
                "List tools exposed by connected MCP servers (filesystem, databases, browser, etc.).",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String args) {
        Map<String, List<McpTool>> tools = mcp.tools();
        if (tools.isEmpty()) {
            return "No MCP servers connected. Configure jarvis.mcp.servers (e.g. command 'npx', args "
                    + "['-y','@modelcontextprotocol/server-filesystem','<path>']) to mount one.";
        }
        StringBuilder sb = new StringBuilder();
        tools.forEach((server, list) -> {
            sb.append("● ").append(server).append('\n');
            for (McpTool t : list) {
                sb.append("    - ").append(t.name());
                if (!t.description().isBlank()) {
                    sb.append(" — ").append(t.description());
                }
                sb.append('\n');
            }
        });
        return sb.toString().trim();
    }
}
