package com.jarvis.revenue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.revenue.TemplateSpec.PropSpec;

class NotionPropertyMapperTest {

    private final ObjectMapper m = new ObjectMapper();

    @Test
    void schemaShapesTypesAndKeepsOneTitle() {
        ObjectNode s = NotionPropertyMapper.schema(List.of(
                new PropSpec("Task", "title", List.of()),
                new PropSpec("Status", "select", List.of("Todo", "Done")),
                new PropSpec("Amount", "number", List.of())), m);
        assertThat(s.path("Task").has("title")).isTrue();
        assertThat(s.path("Status").path("select").path("options").get(0).path("name").asText()).isEqualTo("Todo");
        assertThat(s.path("Amount").has("number")).isTrue();
    }

    @Test
    void forcesATitleWhenNoneDeclared() {
        ObjectNode s = NotionPropertyMapper.schema(List.of(new PropSpec("Note", "rich_text", List.of())), m);
        assertThat(s.path("Note").has("title")).isTrue();   // promoted to title
    }

    @Test
    void valuesShapeByType() {
        var props = List.of(new PropSpec("Task", "title", List.of()),
                new PropSpec("Status", "select", List.of()),
                new PropSpec("Amount", "number", List.of()),
                new PropSpec("Done", "checkbox", List.of()));
        ObjectNode v = NotionPropertyMapper.values(
                Map.of("Task", "Submit", "Status", "Todo", "Amount", "42", "Done", "yes"), props, m);
        assertThat(v.path("Task").path("title").get(0).path("text").path("content").asText()).isEqualTo("Submit");
        assertThat(v.path("Status").path("select").path("name").asText()).isEqualTo("Todo");
        assertThat(v.path("Amount").path("number").asInt()).isEqualTo(42);
        assertThat(v.path("Done").path("checkbox").asBoolean()).isTrue();
    }
}
