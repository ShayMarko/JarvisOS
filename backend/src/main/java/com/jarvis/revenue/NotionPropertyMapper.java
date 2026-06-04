package com.jarvis.revenue;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.jarvis.revenue.TemplateSpec.PropSpec;

/**
 * Pure mapping between a {@link TemplateSpec} database's properties and Notion's JSON shapes — both the
 * database SCHEMA (property definitions) and a row's VALUES. No network, fully unit-testable. Guarantees
 * Notion's rule that a database has exactly one {@code title} property (promotes the first if missing).
 */
final class NotionPropertyMapper {

    private NotionPropertyMapper() {}

    /** Build the Notion {@code properties} schema object for a database from its property specs. */
    static ObjectNode schema(List<PropSpec> props, ObjectMapper m) {
        ObjectNode out = m.createObjectNode();
        boolean titleAssigned = false;
        for (int i = 0; i < props.size(); i++) {
            PropSpec p = props.get(i);
            String type = normalize(p.type());
            // Notion requires exactly one title property; force the first if none declared one.
            if ("title".equals(type) && titleAssigned) {
                type = "rich_text";
            }
            if ("title".equals(type)) {
                titleAssigned = true;
            }
            out.set(p.name(), defForType(type, p, m));
        }
        if (!titleAssigned && !props.isEmpty()) {
            out.set(props.get(0).name(), m.createObjectNode().set("title", m.createObjectNode()));
        }
        return out;
    }

    /** Build the Notion property-VALUES object for a single row (column name → value), typed by the schema. */
    static ObjectNode values(Map<String, String> row, List<PropSpec> props, ObjectMapper m) {
        ObjectNode out = m.createObjectNode();
        for (PropSpec p : props) {
            String val = row.get(p.name());
            if (val == null) {
                continue;
            }
            String type = normalize(p.type());
            ObjectNode v = m.createObjectNode();
            switch (type) {
                case "title" -> v.set("title", richText(val, m));
                case "rich_text" -> v.set("rich_text", richText(val, m));
                case "number" -> v.put("number", parseNum(val));
                case "checkbox" -> v.put("checkbox", "true".equalsIgnoreCase(val) || "yes".equalsIgnoreCase(val));
                case "select" -> v.putObject("select").put("name", val);
                case "multi_select" -> {
                    var arr = v.putArray("multi_select");
                    for (String part : val.split("\\s*,\\s*")) {
                        if (!part.isBlank()) {
                            arr.addObject().put("name", part);
                        }
                    }
                }
                case "date" -> v.putObject("date").put("start", val);
                default -> v.set("rich_text", richText(val, m));
            }
            out.set(p.name(), v);
        }
        return out;
    }

    private static ObjectNode defForType(String type, PropSpec p, ObjectMapper m) {
        ObjectNode def = m.createObjectNode();
        switch (type) {
            case "title" -> def.set("title", m.createObjectNode());
            case "number" -> def.set("number", m.createObjectNode());
            case "date" -> def.set("date", m.createObjectNode());
            case "checkbox" -> def.set("checkbox", m.createObjectNode());
            case "select", "multi_select" -> {
                ObjectNode body = m.createObjectNode();
                var opts = body.putArray("options");
                if (p.options() != null) {
                    for (String o : p.options()) {
                        opts.addObject().put("name", o);
                    }
                }
                def.set(type, body);
            }
            default -> def.set("rich_text", m.createObjectNode());
        }
        return def;
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode richText(String text, ObjectMapper m) {
        var arr = m.createArrayNode();
        arr.addObject().putObject("text").put("content", text);
        return arr;
    }

    private static double parseNum(String s) {
        try {
            return Double.parseDouble(s.replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalize(String type) {
        String t = type == null ? "" : type.trim().toLowerCase();
        return switch (t) {
            case "title", "rich_text", "text", "number", "select", "multi_select", "date", "checkbox" ->
                    "text".equals(t) ? "rich_text" : t;
            default -> "rich_text";
        };
    }
}
