package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.web.WebSearchService;

import lombok.RequiredArgsConstructor;

/** Keyless web search for an instant answer (DuckDuckGo). */
@Component
@RequiredArgsConstructor
public class WebSearchTool implements Tool {

    private final WebSearchService web;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("web_search", "Search the web for an instant answer (DuckDuckGo, no key).",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            return web.search(ToolArgs.str(mapper, args, "query"));
        } catch (Exception e) {
            return "Web search error: " + e.getMessage();
        }
    }
}
