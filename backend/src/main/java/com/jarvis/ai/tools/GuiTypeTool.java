package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;
import com.jarvis.security.RiskLevel;

import lombok.RequiredArgsConstructor;

/** Hands-on-Mac: type literal text into the focused field. HIGH-risk → auto-gated to your approval. */
@Component
@RequiredArgsConstructor
public class GuiTypeTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("gui_type",
                "Type literal text into whatever currently has keyboard focus. Click the field first with "
                + "gui_click if needed. High-risk: requires your approval before it runs.",
                "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}");
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
        String text = ToolArgs.firstStr(mapper, args, "text", "value");
        if (text.isBlank()) {
            return "Error: provide 'text' to type.";
        }
        try {
            return mac.typeText(text);
        } catch (Exception e) {
            return "Error typing: " + e.getMessage();
        }
    }
}
