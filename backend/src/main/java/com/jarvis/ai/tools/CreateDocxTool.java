package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.document.DocumentService;

import lombok.RequiredArgsConstructor;

/** Generates a Word .docx in the Jarvis Explorer's Generated folder. */
@Component
@RequiredArgsConstructor
public class CreateDocxTool implements Tool {

    private final DocumentService documents;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_docx", "Create a Word (.docx) document from a title and body text.",
                "{\"type\":\"object\",\"properties\":{\"filename\":{\"type\":\"string\"},"
                + "\"title\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"content\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            String path = documents.createDocx(ToolArgs.str(mapper, args, "filename"),
                    ToolArgs.str(mapper, args, "title"), ToolArgs.str(mapper, args, "content"));
            return "Created " + path;
        } catch (Exception e) {
            return "Error creating DOCX: " + e.getMessage();
        }
    }
}
