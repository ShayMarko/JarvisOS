package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.document.DocumentService;

import lombok.RequiredArgsConstructor;

/** Generates a PDF in the Jarvis Explorer's Generated folder. */
@Component
@RequiredArgsConstructor
public class CreatePdfTool implements Tool {

    private final DocumentService documents;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_pdf",
                "Create a PDF document from a title and body text. Optional 'folder' (Explorer-relative, e.g. "
                + "'Books/my-book') stores it in that project folder instead of the shared Generated folder.",
                "{\"type\":\"object\",\"properties\":{\"folder\":{\"type\":\"string\"},\"filename\":{\"type\":\"string\"},"
                + "\"title\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"content\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        try {
            String folder = ToolArgs.firstStr(mapper, args, "folder", "dir", "path");
            String path = documents.createPdf(folder, ToolArgs.str(mapper, args, "filename"),
                    ToolArgs.str(mapper, args, "title"), ToolArgs.str(mapper, args, "content"));
            return "Created " + path;
        } catch (Exception e) {
            return "Error creating PDF: " + e.getMessage();
        }
    }
}
