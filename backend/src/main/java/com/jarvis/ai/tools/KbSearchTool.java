package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.kb.KnowledgeBaseService;
import com.jarvis.kb.SearchHit;

import lombok.RequiredArgsConstructor;

/** Searches the indexed Knowledge Base and returns relevant passages with sources. */
@Component
@RequiredArgsConstructor
public class KbSearchTool implements Tool {

    private final KnowledgeBaseService kb;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("kb_search",
                "Search the indexed Knowledge Base (the user's documents) and return relevant passages with sources.",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            List<SearchHit> hits = kb.search(ToolArgs.str(mapper, args, "query"), 4);
            if (hits.isEmpty()) {
                return "No relevant documents in the Knowledge Base.";
            }
            StringBuilder sb = new StringBuilder();
            for (SearchHit h : hits) {
                String snippet = h.content().length() > 400 ? h.content().substring(0, 400) + "…" : h.content();
                sb.append("[").append(h.title()).append("] (score ")
                        .append(String.format("%.2f", h.score())).append(")\n")
                        .append(snippet).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error searching knowledge base: " + e.getMessage();
        }
    }
}
