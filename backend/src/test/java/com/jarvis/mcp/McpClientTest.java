package com.jarvis.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.mcp.McpClient.McpTool;

class McpClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Canned server stdout: initialize result, an interleaved notification, tools/list, tools/call. */
    private static final String SERVER_OUT = String.join("\n",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"serverInfo\":{\"name\":\"fs\"}}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/message\",\"params\":{\"level\":\"info\"}}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"read_file\",\"description\":\"Read a file\",\"inputSchema\":{\"type\":\"object\"}}]}}",
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"file contents here\"}]}}",
            "");

    @Test
    void handshakeDiscoversToolsAndCalls() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(SERVER_OUT.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        McpClient client = new McpClient(in, out, mapper, null);

        client.initialize();
        List<McpTool> tools = client.listTools();
        assertThat(tools).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("read_file");
            assertThat(t.description()).isEqualTo("Read a file");
        });

        String result = client.callTool("read_file", mapper.createObjectNode().put("path", "/x"));
        assertThat(result).isEqualTo("file contents here");

        // The client must have written initialize + initialized notification + tools/list + tools/call.
        String sent = out.toString(StandardCharsets.UTF_8);
        assertThat(sent).contains("\"method\":\"initialize\"");
        assertThat(sent).contains("\"method\":\"notifications/initialized\"");
        assertThat(sent).contains("\"method\":\"tools/list\"");
        assertThat(sent).contains("\"method\":\"tools/call\"").contains("read_file");
    }

    @Test
    void flagsToolErrors() {
        var result = mapper.createObjectNode();
        result.put("isError", true);
        result.putArray("content").addObject().put("type", "text").put("text", "boom");
        assertThat(McpClient.extractText(result, mapper)).contains("error").contains("boom");
    }
}
