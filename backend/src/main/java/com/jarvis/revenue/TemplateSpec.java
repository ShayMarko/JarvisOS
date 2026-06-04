package com.jarvis.revenue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A complete Notion-template blueprint produced by ONE AI planning call (or a cached seed). The
 * deterministic {@link NotionTemplateBuilder} turns it into real Notion pages/databases/rows with
 * zero further AI calls — the cost-control core of the RevenueOS template pipeline.
 */
public record TemplateSpec(
        String title,
        String language,
        List<DbSpec> databases,
        List<PageSpec> pages,
        String salesCopy) {

    /** A database: a name, typed properties, and optional sample rows (column→value). */
    public record DbSpec(String name, List<PropSpec> properties, List<Map<String, String>> sampleRows) {}

    /** A property: name, Notion type (title|rich_text|number|select|multi_select|date|checkbox), select options. */
    public record PropSpec(String name, String type, List<String> options) {}

    /** A content page: a title and a list of block lines (markdown-ish: "# h", "- [ ] todo", plain = paragraph). */
    public record PageSpec(String title, List<String> blocks) {}

    /** Parse a model/JSON spec defensively into a TemplateSpec. */
    public static TemplateSpec fromJson(JsonNode n) {
        String title = n.path("title").asText("Untitled Template");
        String language = n.path("language").asText("en");
        List<DbSpec> dbs = new ArrayList<>();
        for (JsonNode d : n.path("databases")) {
            List<PropSpec> props = new ArrayList<>();
            for (JsonNode p : d.path("properties")) {
                List<String> opts = new ArrayList<>();
                p.path("options").forEach(o -> opts.add(o.asText()));
                props.add(new PropSpec(p.path("name").asText(""), p.path("type").asText("rich_text"), opts));
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (JsonNode r : d.path("sampleRows")) {
                Map<String, String> row = new LinkedHashMap<>();
                r.fields().forEachRemaining(e -> row.put(e.getKey(), e.getValue().asText("")));
                rows.add(row);
            }
            dbs.add(new DbSpec(d.path("name").asText("Database"), props, rows));
        }
        List<PageSpec> pages = new ArrayList<>();
        for (JsonNode pg : n.path("pages")) {
            List<String> blocks = new ArrayList<>();
            pg.path("blocks").forEach(b -> blocks.add(b.asText("")));
            pages.add(new PageSpec(pg.path("title").asText("Page"), blocks));
        }
        return new TemplateSpec(title, language, dbs, pages, n.path("salesCopy").asText(""));
    }
}
