package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;
import com.jarvis.security.RiskLevel;

import lombok.RequiredArgsConstructor;

/** Hands-on-Mac: open a URL in the default browser. HIGH-risk → auto-gated to your approval. */
@Component
@RequiredArgsConstructor
public class OpenUrlTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("open_url",
                "Open a URL in the default browser (e.g. to start a web task). High-risk: requires your approval.",
                "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public String execute(String args) {
        String url = ToolArgs.firstStr(mapper, args, "url", "link");
        if (url.isBlank()) {
            return "Error: provide a 'url' to open.";
        }
        try {
            return mac.openUrl(url);
        } catch (Exception e) {
            return "Error opening URL: " + e.getMessage();
        }
    }
}
