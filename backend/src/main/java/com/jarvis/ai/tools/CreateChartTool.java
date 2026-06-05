package com.jarvis.ai.tools;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.document.DocumentService;

import lombok.RequiredArgsConstructor;

/**
 * Render a chart from data on request — "make me a bar chart of X". Deterministic, no AI cost: writes a
 * clean SVG to the Generated folder (openable in any browser). Accepts data as a list of {label,value}
 * objects, or parallel labels[]/values[] arrays.
 */
@Component
@RequiredArgsConstructor
public class CreateChartTool implements Tool {

    private final DocumentService documents;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_chart",
                "Render a chart (bar | line | pie) from data and save it as an SVG image in the Generated folder. "
                + "Use for 'make a chart/graph of …'. Provide 'type', optional 'title'/'filename', and the data as "
                + "either 'data':[{\"label\":\"A\",\"value\":3}] or parallel 'labels' + 'values' arrays.",
                "{\"type\":\"object\",\"properties\":{"
                + "\"type\":{\"type\":\"string\",\"enum\":[\"bar\",\"line\",\"pie\"]},"
                + "\"title\":{\"type\":\"string\"},\"filename\":{\"type\":\"string\"},"
                + "\"data\":{\"type\":\"array\",\"items\":{\"type\":\"object\","
                + "\"properties\":{\"label\":{\"type\":\"string\"},\"value\":{\"type\":\"number\"}}}},"
                + "\"labels\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"values\":{\"type\":\"array\",\"items\":{\"type\":\"number\"}}}}");
    }

    @Override
    public boolean mutates() {
        return true;   // writes a file artifact
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode root = ToolArgs.root(mapper, argumentsJson);
            String type = text(root, "type", "bar");
            String title = text(root, "title", "");
            String filename = text(root, "filename", title.isBlank() ? "chart" : title);

            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            JsonNode data = root.get("data");
            if (data != null && data.isArray() && data.size() > 0) {
                for (JsonNode row : data) {
                    labels.add(text(row, "label", "?"));
                    values.add(row.path("value").asDouble(0));
                }
            } else {
                JsonNode ls = root.get("labels");
                JsonNode vs = root.get("values");
                if (vs != null && vs.isArray()) {
                    for (int i = 0; i < vs.size(); i++) {
                        values.add(vs.get(i).asDouble(0));
                        labels.add(ls != null && ls.isArray() && i < ls.size() ? ls.get(i).asText() : String.valueOf(i + 1));
                    }
                }
            }
            if (values.isEmpty()) {
                return "Provide chart data — either 'data':[{label,value}] or 'labels'+'values' arrays.";
            }
            String path = documents.createChart(filename, type, title, labels, values);
            return "Saved the " + type + " chart to " + path + " (" + values.size() + " data points).";
        } catch (Exception e) {
            return "Couldn't build the chart: " + e.getMessage();
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? fallback : v.asText();
    }
}
