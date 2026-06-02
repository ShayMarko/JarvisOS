package com.jarvis.command.handlers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.mcp.McpClient.McpTool;
import com.jarvis.mcp.McpManager;

import lombok.RequiredArgsConstructor;

/** {@code /mcp} — shows connected MCP servers and the tools they expose. */
@Component
@RequiredArgsConstructor
public class McpHandler implements CommandHandler {

    private final McpManager mcp;

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("mcp", "/mcp",
                "Show connected MCP servers and their tools.", CommandCategory.AGENTS);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        Map<String, List<McpTool>> tools = mcp.tools();
        Map<String, Object> data = new LinkedHashMap<>();
        tools.forEach((server, list) -> data.put(server,
                list.stream().map(t -> t.name() + (t.description().isBlank() ? "" : " — " + t.description())).toList()));
        String message = tools.isEmpty()
                ? "No MCP servers connected. Configure jarvis.mcp.servers to mount one (e.g. the filesystem or Postgres MCP server)."
                : "Connected MCP servers: " + String.join(", ", tools.keySet());
        return CommandResult.ok("mcp", message, data);
    }
}
