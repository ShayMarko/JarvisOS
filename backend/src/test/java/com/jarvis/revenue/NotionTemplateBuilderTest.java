package com.jarvis.revenue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.document.DocumentService;
import com.jarvis.revenue.NotionTemplateBuilder.BuildResult;

class NotionTemplateBuilderTest {

    /** Records what the builder asks Notion to do, returning fake ids. */
    static class FakeNotionApi implements NotionApi {
        final List<String> pages = new ArrayList<>();
        int databases = 0;
        int rows = 0;
        public boolean available() { return true; }
        public String createPage(String parent, String title, List<String> blocks) {
            pages.add(title);
            return "page_" + pages.size();
        }
        public String createDatabase(String parent, String name, ObjectNode schema) {
            databases++;
            return "db_" + databases;
        }
        public void addRow(String dbId, ObjectNode values) { rows++; }
    }

    @Test
    void buildsRootPageDatabasesRowsPagesAndSalesPdf() {
        FakeNotionApi api = new FakeNotionApi();
        DocumentService docs = mock(DocumentService.class);
        when(docs.createPdf(any(), any(), any(), any())).thenReturn("RevenueOS/demo/sales-copy.pdf");
        NotionTemplateBuilder builder = new NotionTemplateBuilder(api, docs, new ObjectMapper(),
                mock(RevenueService.class));

        TemplateSpec spec = new TemplateSpec("Demo", "en",
                List.of(new TemplateSpec.DbSpec("Tasks",
                        List.of(new TemplateSpec.PropSpec("Task", "title", List.of()),
                                new TemplateSpec.PropSpec("Status", "select", List.of("Todo", "Done"))),
                        List.of(Map.of("Task", "A", "Status", "Todo"), Map.of("Task", "B", "Status", "Done")))),
                List.of(new TemplateSpec.PageSpec("Dashboard", List.of("# Hi")),
                        new TemplateSpec.PageSpec("Guide", List.of("how to"))),
                "Buy my template!");

        BuildResult r = builder.build(spec, "parent123");

        assertThat(r.notionRootPageId()).isEqualTo("page_1");   // root created first
        assertThat(r.createdDatabases()).isEqualTo(1);
        assertThat(r.createdPages()).isEqualTo(3);              // root + Dashboard + Guide
        assertThat(api.rows).isEqualTo(2);                      // two sample rows
        assertThat(r.salesCopyPath()).isEqualTo("RevenueOS/demo/sales-copy.pdf");
        assertThat(r.manualSteps()).anyMatch(s -> s.toLowerCase().contains("publish"));
    }
}
