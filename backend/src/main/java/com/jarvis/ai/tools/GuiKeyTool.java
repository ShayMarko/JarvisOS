package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.local.MacActions;
import com.jarvis.security.RiskLevel;

import lombok.RequiredArgsConstructor;

/** Hands-on-Mac: press a key (or combo). HIGH-risk → auto-gated to your approval. */
@Component
@RequiredArgsConstructor
public class GuiKeyTool implements Tool {

    private final MacActions mac;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("gui_key",
                "Press a keyboard key, optionally with modifiers. 'key' is a named key (return, tab, escape, "
                + "space, delete, up/down/left/right, home, end) or a single character. 'modifiers' is optional, "
                + "e.g. 'cmd', 'cmd+shift', 'ctrl'. Use for shortcuts like cmd+s. High-risk: requires your approval.",
                "{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"string\"},"
                + "\"modifiers\":{\"type\":\"string\",\"description\":\"e.g. cmd, cmd+shift, ctrl, opt\"}},"
                + "\"required\":[\"key\"]}");
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
        String key = ToolArgs.firstStr(mapper, args, "key", "keys");
        if (key.isBlank()) {
            return "Error: provide a 'key' to press.";
        }
        try {
            return mac.pressKey(key, ToolArgs.firstStr(mapper, args, "modifiers", "mods"));
        } catch (Exception e) {
            return "Error pressing key: " + e.getMessage();
        }
    }
}
