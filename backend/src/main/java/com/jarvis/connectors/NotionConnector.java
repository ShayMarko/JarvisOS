package com.jarvis.connectors;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Real Notion connector — calls the Notion API with an integration token from the
 * Secrets Vault (entry {@code notion-token}). Like the other key-gated connectors
 * it makes a genuine HTTP call (401s without a valid token).
 */
@Component
@RequiredArgsConstructor
public class NotionConnector implements Connector {

    private static final String NOTION_VERSION = "2022-06-28";
    private final RestClient client = RestClient.create("https://api.notion.com");
    private final ObjectMapper mapper;

    @Override public String id() { return "notion"; }
    @Override public String name() { return "Notion"; }
    @Override public String category() { return "Productivity"; }
    @Override public String requiredSecret() { return "notion-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("search", "Search", "Search your pages and databases {query?}"),
                new ConnectorAction("get_page", "Read a page", "Read a page's title {page_id}"),
                new ConnectorAction("create_page", "Create a page", "Create a page {parent_page_id|database_id, title, content?}"),
                new ConnectorAction("append_text", "Append text", "Append paragraphs to a page {page_id, text}"),
                new ConnectorAction("query_database", "Query a database", "List rows in a database {database_id, page_size?}"),
                new ConnectorAction("create_database", "Create a database", "Create a DB under a page {parent_page_id, title, properties}"),
                new ConnectorAction("add_row", "Add a database row", "Add a row {database_id, properties}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        return switch (actionId) {
            case "search" -> search(a, token);
            case "get_page" -> getPage(a, token);
            case "create_page" -> createPage(a, token);
            case "append_text" -> appendText(a, token);
            case "query_database" -> queryDatabase(a, token);
            case "create_database" -> createDatabase(a, token);
            case "add_row" -> addRow(a, token);
            default -> throw new NotFoundException("Unknown Notion action '" + actionId + "'");
        };
    }

    /** Create a database under a parent page. {@code properties} is a Notion-shaped property schema object. */
    private String createDatabase(JsonNode a, String token) throws Exception {
        String parent = require(a, "parent_page_id");
        String title = a.path("title").asText("");
        JsonNode props = a.path("properties");
        if (title.isBlank() || !props.isObject() || props.isEmpty()) {
            return "Error: provide 'parent_page_id', 'title', and a non-empty 'properties' schema.";
        }
        ObjectNode body = mapper.createObjectNode();
        body.putObject("parent").put("type", "page_id").put("page_id", parent);
        ArrayNode titleArr = body.putArray("title");
        titleArr.addObject().putObject("text").put("content", title);
        body.set("properties", props);
        JsonNode r = mapper.readTree(post("/v1/databases", body, token));
        return "Created database '" + title + "': " + r.path("id").asText("(ok)");
    }

    /** Add a row (page) to a database. {@code properties} is a Notion-shaped property-values object. */
    private String addRow(JsonNode a, String token) throws Exception {
        String dbId = require(a, "database_id");
        JsonNode props = a.path("properties");
        if (!props.isObject() || props.isEmpty()) {
            return "Error: provide 'database_id' and a non-empty 'properties' object.";
        }
        ObjectNode body = mapper.createObjectNode();
        body.putObject("parent").put("database_id", dbId);
        body.set("properties", props);
        mapper.readTree(post("/v1/pages", body, token));
        return "Added a row to database " + dbId + ".";
    }

    private String search(JsonNode args, String token) throws Exception {
        Map<String, Object> body = Map.of("query", args.path("query").asText(""), "page_size", 10);
        JsonNode results = mapper.readTree(post("/v1/search", body, token)).path("results");
        StringBuilder sb = new StringBuilder();
        for (JsonNode r : results) {
            String type = r.path("object").asText("");
            String title = extractTitle(r);
            String url = r.path("url").asText("");
            sb.append("• [").append(type).append("] ").append(title.isBlank() ? "(untitled)" : title)
                    .append("  ").append(r.path("id").asText());
            if (!url.isBlank()) {
                sb.append("\n  ").append(url);
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? "No Notion results." : sb.toString().trim();
    }

    private String getPage(JsonNode a, String token) throws Exception {
        String id = require(a, "page_id");
        JsonNode p = mapper.readTree(client.get().uri("/v1/pages/" + id)
                .header("Authorization", "Bearer " + token).header("Notion-Version", NOTION_VERSION)
                .retrieve().body(String.class));
        String title = extractTitle(p);
        return "Page " + id + ": " + (title.isBlank() ? "(untitled)" : title)
                + (p.has("url") ? "\n" + p.path("url").asText() : "");
    }

    private String createPage(JsonNode a, String token) throws Exception {
        String title = a.path("title").asText("");
        if (title.isBlank()) {
            return "Error: provide a 'title' for the page.";
        }
        String pageParent = a.path("parent_page_id").asText("");
        String dbParent = a.path("database_id").asText("");
        if (pageParent.isBlank() && dbParent.isBlank()) {
            return "Error: provide a 'parent_page_id' or 'database_id'.";
        }
        ObjectNode body = mapper.createObjectNode();
        ObjectNode parent = body.putObject("parent");
        ObjectNode props = body.putObject("properties");
        if (!dbParent.isBlank()) {
            parent.put("database_id", dbParent);
            props.set("Name", titleProp(title));     // a database's title property is conventionally "Name"
        } else {
            parent.put("page_id", pageParent);
            props.set("title", titleProp(title));     // a page under a page uses the "title" property
        }
        String content = a.path("content").asText("");
        if (!content.isBlank()) {
            body.set("children", paragraphs(content));
        }
        JsonNode r = mapper.readTree(post("/v1/pages", body, token));
        return "Created page: " + r.path("url").asText(r.path("id").asText("(ok)"));
    }

    private String appendText(JsonNode a, String token) throws Exception {
        String id = a.path("page_id").asText(a.path("block_id").asText(""));
        String text = a.path("text").asText("");
        if (id.isBlank() || text.isBlank()) {
            return "Error: provide 'page_id' and 'text'.";
        }
        ObjectNode body = mapper.createObjectNode();
        body.set("children", paragraphs(text));
        client.patch().uri("/v1/blocks/" + id + "/children")
                .header("Authorization", "Bearer " + token).header("Notion-Version", NOTION_VERSION)
                .header("Content-Type", "application/json").body(body.toString())
                .retrieve().toBodilessEntity();
        return "Appended " + text.lines().count() + " paragraph(s) to " + id + ".";
    }

    private String queryDatabase(JsonNode a, String token) throws Exception {
        String id = require(a, "database_id");
        Map<String, Object> body = Map.of("page_size", Math.min(50, Math.max(1, a.path("page_size").asInt(20))));
        JsonNode results = mapper.readTree(post("/v1/databases/" + id + "/query", body, token)).path("results");
        StringBuilder sb = new StringBuilder();
        for (JsonNode row : results) {
            String title = extractTitle(row);
            sb.append("• ").append(title.isBlank() ? "(untitled)" : title)
                    .append("  (").append(row.path("id").asText()).append(")\n");
        }
        return sb.length() == 0 ? "No rows." : sb.toString().trim();
    }

    private String post(String path, Object body, String token) {
        return client.post().uri(path)
                .header("Authorization", "Bearer " + token).header("Notion-Version", NOTION_VERSION)
                .header("Content-Type", "application/json").body(body)
                .retrieve().body(String.class);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode titleProp(String title) {
        ObjectNode prop = mapper.createObjectNode();
        ObjectNode t = prop.putArray("title").addObject();
        t.putObject("text").put("content", title);
        return prop;
    }

    private com.fasterxml.jackson.databind.node.ArrayNode paragraphs(String content) {
        com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
        for (String line : content.split("\n", -1)) {
            ObjectNode block = arr.addObject();
            block.put("object", "block");
            block.put("type", "paragraph");
            block.putObject("paragraph").putArray("rich_text").addObject()
                    .put("type", "text").putObject("text").put("content", line);
        }
        return arr;
    }

    private static String require(JsonNode a, String key) {
        String v = a.path(key).asText("");
        if (v.isBlank()) {
            throw new NotFoundException("Provide '" + key + "'.");
        }
        return v;
    }

    /** Notion titles live under a "title" rich-text array somewhere in properties. */
    private String extractTitle(JsonNode result) {
        JsonNode props = result.path("properties");
        for (JsonNode prop : props) {
            if ("title".equals(prop.path("type").asText())) {
                JsonNode arr = prop.path("title");
                if (arr.isArray() && !arr.isEmpty()) {
                    return arr.get(0).path("plain_text").asText("");
                }
            }
        }
        return "";
    }
}
