package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.revenue.NotionTemplateBuilder;
import com.jarvis.revenue.NotionTemplateBuilder.BuildResult;
import com.jarvis.revenue.TemplateSpecGenerator;
import com.jarvis.revenue.TemplateSpecGenerator.Result;

import lombok.RequiredArgsConstructor;

/**
 * RevenueOS: build a complete, sellable Notion template from an idea — ONE planning call → a JSON spec →
 * deterministic Notion build (pages, databases, sample rows) + a sales-copy PDF. Cheap by design.
 */
@Component
@RequiredArgsConstructor
public class BuildNotionTemplateTool implements Tool {

    private final TemplateSpecGenerator generator;
    private final NotionTemplateBuilder builder;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("build_notion_template",
                "Create a complete sellable Notion template from an 'idea' and build it in Notion under "
                + "'parent_page_id'. Optional 'language' (e.g. he/en), 'target_customer', 'complexity'. Needs a "
                + "'notion-token' in the vault. Returns the created pages/databases + manual publish steps.",
                "{\"type\":\"object\",\"properties\":{\"idea\":{\"type\":\"string\"},\"parent_page_id\":{\"type\":\"string\"},"
                + "\"language\":{\"type\":\"string\"},\"target_customer\":{\"type\":\"string\"},\"complexity\":{\"type\":\"string\"}},"
                + "\"required\":[\"idea\",\"parent_page_id\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        String idea = ToolArgs.firstStr(mapper, args, "idea", "template", "product");
        String parent = ToolArgs.firstStr(mapper, args, "parent_page_id", "parent", "page_id");
        if (idea.isBlank()) {
            return "Provide the template 'idea'.";
        }
        if (!builder.available()) {
            return "Add a 'notion-token' to the Secrets Vault first (the Notion integration token).";
        }
        if (parent.isBlank()) {
            return "Provide 'parent_page_id' — a Notion page the integration can access (the template is built under it).";
        }
        try {
            Result gen = generator.generate(idea, ToolArgs.firstStr(mapper, args, "language", "lang"),
                    ToolArgs.firstStr(mapper, args, "target_customer", "audience"),
                    ToolArgs.firstStr(mapper, args, "complexity"));
            BuildResult b = builder.build(gen.spec(), parent);
            return "✅ Built '" + gen.spec().title() + "' in Notion (" + gen.aiCallsUsed() + " AI call): "
                    + b.createdPages() + " page(s), " + b.createdDatabases() + " database(s). Root: " + b.notionRootPageId()
                    + (b.salesCopyPath().isBlank() ? "" : "\nSales copy: " + b.salesCopyPath())
                    + "\nNext (manual in Notion): " + String.join("; ", b.manualSteps());
        } catch (Exception e) {
            return "Couldn't build the template: " + e.getMessage();
        }
    }
}
