package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.explorer.FileNode;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/** Searches files by name across the Jarvis Explorer. */
@Component
@RequiredArgsConstructor
public class SearchFilesTool implements Tool {

    private final FileSystemService fs;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("search_files", "Search files by name across the Jarvis Explorer.",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            List<FileNode> hits = fs.search(ToolArgs.str(mapper, args, "query"), "");
            if (hits.isEmpty()) {
                return "No matching files.";
            }
            return hits.stream().map(FileNode::path).reduce((a, b) -> a + "\n" + b).orElse("");
        } catch (Exception e) {
            return "Error searching: " + e.getMessage();
        }
    }
}
