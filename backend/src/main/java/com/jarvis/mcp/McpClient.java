package com.jarvis.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A minimal MCP (Model Context Protocol) client speaking JSON-RPC 2.0 over the
 * stdio transport (newline-delimited JSON). Implements the handshake, tool
 * discovery and tool invocation:
 * {@code initialize} → {@code notifications/initialized} → {@code tools/list} → {@code tools/call}.
 *
 * <p>Decoupled from process management (takes raw streams) so the JSON-RPC framing
 * is unit-testable without spawning a process; {@link McpManager} wires it to a
 * real {@link Process}.
 */
public class McpClient implements AutoCloseable {

    /** A tool advertised by an MCP server. */
    public record McpTool(String name, String description, JsonNode inputSchema) {}

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper mapper;
    private final BufferedReader reader;
    private final Writer writer;
    private final Runnable onClose;
    private final AtomicInteger ids = new AtomicInteger();

    public McpClient(InputStream in, OutputStream out, ObjectMapper mapper, Runnable onClose) {
        this.mapper = mapper;
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        this.onClose = onClose;
    }

    /** Handshake: initialize then send the initialized notification. */
    public void initialize() throws IOException {
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", mapper.createObjectNode());
        ObjectNode info = params.putObject("clientInfo");
        info.put("name", "JarvisAIOS");
        info.put("version", "1.0");
        request("initialize", params);
        notification("notifications/initialized", mapper.createObjectNode());
    }

    /** Discover the server's tools via {@code tools/list}. */
    public List<McpTool> listTools() throws IOException {
        JsonNode result = request("tools/list", mapper.createObjectNode());
        List<McpTool> tools = new ArrayList<>();
        for (JsonNode t : result.path("tools")) {
            tools.add(new McpTool(
                    t.path("name").asText(),
                    t.path("description").asText(""),
                    t.path("inputSchema")));
        }
        return tools;
    }

    /** Invoke a tool via {@code tools/call}; returns the concatenated text content. */
    public String callTool(String name, JsonNode arguments) throws IOException {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments == null ? mapper.createObjectNode() : arguments);
        JsonNode result = request("tools/call", params);
        return extractText(result);
    }

    /** Flatten an MCP tool result's content blocks into plain text. */
    static String extractText(JsonNode result, ObjectMapper mapper) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : result.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        if (sb.length() == 0 && result.has("content")) {
            return result.path("content").toString();
        }
        boolean isError = result.path("isError").asBoolean(false);
        String text = sb.toString().trim();
        return isError ? "Tool reported an error: " + text : text;
    }

    private String extractText(JsonNode result) {
        return extractText(result, mapper);
    }

    private JsonNode request(String method, JsonNode params) throws IOException {
        int id = ids.incrementAndGet();
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        req.set("params", params);
        writeMessage(req);
        return readResult(id, method);
    }

    private void notification(String method, JsonNode params) throws IOException {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("method", method);
        req.set("params", params);
        writeMessage(req);
    }

    private void writeMessage(JsonNode message) throws IOException {
        writer.write(mapper.writeValueAsString(message));
        writer.write("\n");
        writer.flush();
    }

    /** Read newline-delimited JSON until the response with {@code id} arrives. */
    private JsonNode readResult(int id, String method) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode msg = mapper.readTree(line);
            if (msg.path("id").isInt() && msg.path("id").asInt() == id) {
                if (msg.has("error")) {
                    throw new IOException("MCP " + method + " error: " + msg.path("error").path("message").asText());
                }
                return msg.path("result");
            }
            // else: a notification or a different id — skip and keep reading.
        }
        throw new IOException("MCP stream closed before a response to " + method);
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException ignored) {
            // closing
        }
        try {
            reader.close();
        } catch (IOException ignored) {
            // closing
        }
        if (onClose != null) {
            onClose.run();
        }
    }
}
