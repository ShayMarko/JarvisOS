package com.jarvis.revenue;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.secrets.SecretsVaultService;

import lombok.RequiredArgsConstructor;

/** Production {@link NotionApi} — talks to the Notion REST API with the vault's {@code notion-token}. */
@Component
@RequiredArgsConstructor
public class HttpNotionApi implements NotionApi {

    private static final String VERSION = "2022-06-28";

    private final RestClient client = RestClient.create("https://api.notion.com");
    private final SecretsVaultService vault;
    private final ObjectMapper mapper;

    @Override
    public boolean available() {
        return vault.has("notion-token");
    }

    @Override
    public String createPage(String parentPageId, String title, List<String> blocks) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("parent").put("page_id", parentPageId);
        body.putObject("properties").set("title",
                mapper.createObjectNode().set("title", richText(title)));
        if (blocks != null && !blocks.isEmpty()) {
            ArrayNode children = body.putArray("children");
            for (String line : blocks) {
                ObjectNode b = blockFor(line);
                if (b != null) {
                    children.add(b);
                }
            }
        }
        return post("/v1/pages", body).path("id").asText("");
    }

    @Override
    public String createDatabase(String parentPageId, String name, ObjectNode propertiesSchema) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("parent").put("type", "page_id").put("page_id", parentPageId);
        body.putArray("title").addObject().putObject("text").put("content", name);
        body.set("properties", propertiesSchema);
        return post("/v1/databases", body).path("id").asText("");
    }

    @Override
    public void addRow(String databaseId, ObjectNode propertyValues) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("parent").put("database_id", databaseId);
        body.set("properties", propertyValues);
        post("/v1/pages", body);
    }

    /** Convert a markdown-ish line into a Notion block (heading/to-do/bullet/quote/paragraph). */
    private ObjectNode blockFor(String line) {
        String s = line == null ? "" : line.strip();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("### ")) return block("heading_3", s.substring(4), false);
        if (s.startsWith("## ")) return block("heading_2", s.substring(3), false);
        if (s.startsWith("# ")) return block("heading_1", s.substring(2), false);
        if (s.startsWith("- [x] ") || s.startsWith("- [X] ")) return block("to_do", s.substring(6), true);
        if (s.startsWith("- [ ] ")) return block("to_do", s.substring(6), false);
        if (s.startsWith("- ") || s.startsWith("* ")) return block("bulleted_list_item", s.substring(2), false);
        if (s.startsWith("> ")) return block("quote", s.substring(2), false);
        return block("paragraph", s, false);
    }

    private ObjectNode block(String type, String text, boolean checked) {
        ObjectNode b = mapper.createObjectNode();
        b.put("object", "block").put("type", type);
        ObjectNode inner = b.putObject(type);
        inner.set("rich_text", richText(text));
        if ("to_do".equals(type)) {
            inner.put("checked", checked);
        }
        return b;
    }

    private ArrayNode richText(String text) {
        ArrayNode arr = mapper.createArrayNode();
        arr.addObject().putObject("text").put("content", text == null ? "" : text);
        return arr;
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            String raw = client.post().uri(path)
                    .header("Authorization", "Bearer " + vault.revealByName("notion-token"))
                    .header("Notion-Version", VERSION).header("Content-Type", "application/json")
                    .body(body.toString()).retrieve().body(String.class);
            return mapper.readTree(raw == null ? "{}" : raw);
        } catch (Exception e) {
            throw new IllegalStateException("Notion API call failed (" + path + "): " + e.getMessage(), e);
        }
    }
}
