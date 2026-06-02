package com.jarvis.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.mcp.McpClient.McpTool;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * Mounts the configured MCP servers at startup (spec §9 "MCPs"): launches each as
 * a local process, performs the JSON-RPC handshake, discovers its tools, and keeps
 * the connection open for {@code tools/call}. Fully optional — if no servers are
 * configured (or one fails to start), Jarvis logs it and carries on. This is the
 * real MCP transport, so any server (filesystem, Postgres, Playwright, …) plugs in.
 */
@Service
@RequiredArgsConstructor
public class McpManager {

    private static final Logger log = LoggerFactory.getLogger(McpManager.class);

    private final McpProperties props;
    private final ObjectMapper mapper;

    private final Map<String, McpClient> clients = new LinkedHashMap<>();
    private final Map<String, List<McpTool>> toolsByServer = new LinkedHashMap<>();
    private final ExecutorService exec = Executors.newCachedThreadPool();

    @PostConstruct
    void connectAll() {
        for (McpProperties.Server server : props.getServers()) {
            if (!server.isEnabled() || server.getName() == null || server.getCommand() == null) {
                continue;
            }
            try {
                connect(server);
                log.info("MCP server '{}' connected — {} tool(s).", server.getName(),
                        toolsByServer.getOrDefault(server.getName(), List.of()).size());
            } catch (Exception e) {
                log.info("MCP server '{}' unavailable ({}). Skipping — install/configure it to enable its tools.",
                        server.getName(), e.getMessage());
            }
        }
        if (clients.isEmpty() && !props.getServers().isEmpty()) {
            log.info("No MCP servers connected. Tools light up once a configured server is installed.");
        }
    }

    private void connect(McpProperties.Server server) throws Exception {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(server.getCommand());
        cmd.addAll(server.getArgs());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD); // server logs go to its own stderr sink
        Process process = pb.start();
        McpClient client = new McpClient(process.getInputStream(), process.getOutputStream(), mapper, process::destroy);

        // Bound the handshake so a misbehaving server can't stall startup.
        List<McpTool> tools = withTimeout(() -> {
            client.initialize();
            return client.listTools();
        }, props.getStartupTimeoutSeconds());

        clients.put(server.getName(), client);
        toolsByServer.put(server.getName(), tools);
    }

    /** Invoke an MCP tool on a named server; returns its text result. */
    public String call(String serverName, String toolName, String argumentsJson) {
        McpClient client = clients.get(serverName);
        if (client == null) {
            return "No MCP server named '" + serverName + "'. Connected: " + (clients.isEmpty() ? "(none)" : String.join(", ", clients.keySet()));
        }
        try {
            JsonNode args = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            // stdio is a single duplex stream → one call at a time per client.
            synchronized (client) {
                return withTimeout(() -> client.callTool(toolName, args), 60);
            }
        } catch (Exception e) {
            return "MCP call failed: " + e.getMessage();
        }
    }

    /** Server → its advertised tools (immutable snapshot). */
    public Map<String, List<McpTool>> tools() {
        return Map.copyOf(toolsByServer);
    }

    public boolean hasServers() {
        return !clients.isEmpty();
    }

    private <T> T withTimeout(Callable<T> task, int seconds) throws Exception {
        Future<T> future = exec.submit(task);
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            throw e;
        }
    }

    @PreDestroy
    void shutdown() {
        clients.values().forEach(McpClient::close);
        exec.shutdownNow();
    }
}
