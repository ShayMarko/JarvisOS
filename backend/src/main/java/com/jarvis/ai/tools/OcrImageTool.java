package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.document.DocumentService;

import lombok.RequiredArgsConstructor;

/** Extracts text from an Explorer image using the macOS Vision framework. */
@Component
@RequiredArgsConstructor
public class OcrImageTool implements Tool {

    private final DocumentService documents;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("ocr_image", "Extract text from an image in the Jarvis Explorer (macOS Vision OCR).",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            return documents.ocr(ToolArgs.str(mapper, args, "path"));
        } catch (Exception e) {
            return "Error running OCR: " + e.getMessage();
        }
    }
}
