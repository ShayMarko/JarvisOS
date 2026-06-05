package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.document.DocumentService;

import lombok.RequiredArgsConstructor;

/**
 * Generates an SEO-friendly article/blog page (HTML) for a content site — niche guides monetized with
 * affiliate links/ads. Deterministic, no AI cost. The agent writes one per article.
 */
@Component
@RequiredArgsConstructor
public class CreateArticlePageTool implements Tool {

    private final DocumentService documents;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_article_page",
                "Write one SEO article/blog page (HTML) into a content-site folder: proper title + meta "
                + "description, h1/h2 headings, an optional affiliate call-to-action and a disclosure footer. "
                + "Provide 'title', 'description' (meta), 'body' (lite-markdown: '## heading', '- bullet', "
                + "blank-line paragraphs), 'slug', optional 'folder' (e.g. Sites/<site>), 'affiliate_label' "
                + "and 'affiliate_url'.",
                "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},"
                + "\"body\":{\"type\":\"string\"},\"slug\":{\"type\":\"string\"},\"folder\":{\"type\":\"string\"},"
                + "\"affiliate_label\":{\"type\":\"string\"},\"affiliate_url\":{\"type\":\"string\"}},"
                + "\"required\":[\"title\",\"body\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String argumentsJson) {
        String title = ToolArgs.str(mapper, argumentsJson, "title");
        String body = ToolArgs.firstStr(mapper, argumentsJson, "body", "content", "text");
        if (title.isBlank() || body.isBlank()) {
            return "Provide at least 'title' and 'body' for the article.";
        }
        try {
            String path = documents.createArticlePage(
                    ToolArgs.str(mapper, argumentsJson, "folder"),
                    ToolArgs.str(mapper, argumentsJson, "slug"),
                    title,
                    ToolArgs.firstStr(mapper, argumentsJson, "description", "meta", "summary"),
                    body,
                    ToolArgs.firstStr(mapper, argumentsJson, "affiliate_label", "cta"),
                    ToolArgs.firstStr(mapper, argumentsJson, "affiliate_url", "link", "url"));
            return "Wrote the article to " + path + ".";
        } catch (Exception e) {
            return "Couldn't write the article: " + e.getMessage();
        }
    }
}
