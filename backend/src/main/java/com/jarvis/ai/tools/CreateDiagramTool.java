package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.document.DocumentService;

import lombok.RequiredArgsConstructor;

/** Saves a Mermaid diagram and renders it to an image when mmdc is installed. */
@Component
@RequiredArgsConstructor
public class CreateDiagramTool implements Tool {

    private final DocumentService documents;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("create_diagram",
                "Create a diagram from Mermaid source (e.g. 'graph TD; A-->B'). Saves the source and renders to SVG when possible.",
                "{\"type\":\"object\",\"properties\":{\"filename\":{\"type\":\"string\"},"
                + "\"mermaid\":{\"type\":\"string\",\"description\":\"Mermaid diagram source\"}},\"required\":[\"mermaid\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            return documents.createDiagram(ToolArgs.str(mapper, args, "filename"),
                    ToolArgs.str(mapper, args, "mermaid"));
        } catch (Exception e) {
            return "Error creating diagram: " + e.getMessage();
        }
    }
}
