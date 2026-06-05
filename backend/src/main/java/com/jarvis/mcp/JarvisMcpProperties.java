package com.jarvis.mcp;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.mcp} — external MCP (Model Context Protocol) servers Jarvis
 * mounts at startup. Each server is a local process Jarvis speaks JSON-RPC to over
 * stdio; its tools become callable by the agent loop. Empty by default, so the
 * system runs fine with no MCP servers installed.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.mcp")
public class JarvisMcpProperties {

    private List<Server> servers = new ArrayList<>();

    /** Seconds to wait for a server's initialize + tools/list handshake. */
    private int startupTimeoutSeconds = 12;

    @Getter
    @Setter
    public static class Server {
        /** Short id used to address the server (e.g. "filesystem"). */
        private String name;
        /** Executable to launch (e.g. "npx"). */
        private String command;
        /** Arguments (e.g. ["-y", "@modelcontextprotocol/server-filesystem", "/path"]). */
        private List<String> args = new ArrayList<>();
        private boolean enabled = true;
    }
}
