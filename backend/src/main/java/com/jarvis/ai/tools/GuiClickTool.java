package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;
import com.jarvis.security.RiskLevel;

import lombok.RequiredArgsConstructor;

/** Hands-on-Mac: move the mouse and click at screen coordinates. HIGH-risk → auto-gated to your approval. */
@Component
@RequiredArgsConstructor
public class GuiClickTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("gui_click",
                "Move the mouse and click at absolute screen coordinates (x, y). Call see_screen first to locate "
                + "the target. High-risk: requires your approval before it runs.",
                "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"number\"},\"y\":{\"type\":\"number\"}},"
                + "\"required\":[\"x\",\"y\"]}");
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
        try {
            JsonNode n = mapper.readTree(args);
            if (!n.hasNonNull("x") || !n.hasNonNull("y")) {
                return "Error: provide numeric 'x' and 'y' screen coordinates.";
            }
            return mac.clickAt(n.get("x").asInt(), n.get("y").asInt());
        } catch (Exception e) {
            return "Error clicking: " + e.getMessage();
        }
    }
}
