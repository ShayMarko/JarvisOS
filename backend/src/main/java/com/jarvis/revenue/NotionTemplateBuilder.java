package com.jarvis.revenue;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.document.DocumentService;
import com.jarvis.revenue.TemplateSpec.DbSpec;
import com.jarvis.revenue.TemplateSpec.PageSpec;

import lombok.RequiredArgsConstructor;

/**
 * Deterministically turns a {@link TemplateSpec} into a real Notion template — pages, databases,
 * properties, sample rows — with ZERO AI calls (the spec already came from one planning call). Also
 * writes the sales copy to a local PDF. The marketplace publish/duplicate step stays manual (returned).
 */
@Service
@RequiredArgsConstructor
public class NotionTemplateBuilder {

    private static final Logger log = LoggerFactory.getLogger(NotionTemplateBuilder.class);

    private static final List<String> MANUAL_STEPS = List.of(
            "Open the page in Notion and tidy the visual layout",
            "Publish the page (Share → Publish)",
            "Enable 'Duplicate as template'",
            "Copy the public template link for your sales page");

    private final NotionApi notion;
    private final DocumentService documents;
    private final ObjectMapper mapper;
    private final RevenueService revenue;

    public record BuildResult(String notionRootPageId, int createdPages, int createdDatabases,
                              List<String> manualSteps, String salesCopyPath) {}

    public boolean available() {
        return notion.available();
    }

    /** Build the whole template under {@code parentPageId}. Pure orchestration over NotionApi — no AI. */
    public BuildResult build(TemplateSpec spec, String parentPageId) {
        String rootId = notion.createPage(parentPageId, spec.title(),
                List.of("# " + spec.title(), "Built by Jarvis. Tidy the layout, then publish & enable duplication."));
        int pages = 1;
        int dbs = 0;

        for (DbSpec db : spec.databases()) {
            var schema = NotionPropertyMapper.schema(db.properties(), mapper);
            String dbId = notion.createDatabase(rootId, db.name(), schema);
            dbs++;
            if (db.sampleRows() != null) {
                for (var row : db.sampleRows()) {
                    notion.addRow(dbId, NotionPropertyMapper.values(row, db.properties(), mapper));
                }
            }
        }
        for (PageSpec page : spec.pages()) {
            notion.createPage(rootId, page.title(), page.blocks());
            pages++;
        }

        String salesCopyPath = "";
        if (spec.salesCopy() != null && !spec.salesCopy().isBlank()) {
            salesCopyPath = documents.createPdf("RevenueOS/" + slug(spec.title()),
                    "sales-copy", spec.title() + " — Sales Copy", spec.salesCopy());
        }
        revenue.logAsset("Notion template: " + spec.title());   // counts toward the ROI dashboard
        log.info("RevenueOS: built Notion template '{}' — {} page(s), {} database(s).", spec.title(), pages, dbs);
        return new BuildResult(rootId, pages, dbs, MANUAL_STEPS, salesCopyPath);
    }

    private static String slug(String s) {
        return (s == null ? "template" : s).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
