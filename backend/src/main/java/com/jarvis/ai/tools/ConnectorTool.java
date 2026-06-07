package com.jarvis.ai.tools;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.approval.ApprovalResult;
import com.jarvis.approval.ApprovalService;
import com.jarvis.approval.ApprovalStatus;
import com.jarvis.connectors.ConnectorRegistry;
import com.jarvis.security.RiskLevel;

/**
 * Bridges connectors into the agent tool loop (spec §9): one tool lets an agent invoke any connector action,
 * so the Brain reaches external services through the same mechanism as local capabilities.
 *
 * <p>Per-action safety gate: read-only calls run autonomously, but consequential actions a connector flags as
 * HIGH risk (spend money, deploy live, send a message, publish) are routed through the Approval Center — they
 * park in the bell with Approve/Decline and only run once you approve. A "remember this decision" approval
 * lets that exact action auto-run thereafter; this is what makes the autonomous Coordinator safe to arm.
 */
@Component
@RequiredArgsConstructor
public class ConnectorTool implements Tool {

    private final ConnectorRegistry registry;
    private final ObjectMapper mapper;
    @Lazy
    private final ApprovalService approval;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("connector_invoke",
                "Invoke an external connector action (Gmail, GitHub, Stripe, Cloudflare, Ayrshare, …). Read-only "
                + "actions run immediately; actions that spend money / deploy / publish require your approval.",
                "{\"type\":\"object\",\"properties\":{\"connector\":{\"type\":\"string\"},"
                + "\"action\":{\"type\":\"string\"},\"args\":{\"type\":\"object\"}},"
                + "\"required\":[\"connector\",\"action\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode node = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String connector = node.path("connector").asText("");
            String action = node.path("action").asText("");
            String args = node.has("args") ? node.get("args").toString() : "{}";

            RiskLevel risk = registry.actionRisk(connector, action);
            if (risk == RiskLevel.HIGH || risk == RiskLevel.CRITICAL) {
                return gated(connector, action, args, risk);
            }
            return registry.invoke(connector, action, args);
        } catch (Exception e) {
            return "Connector error: " + e.getMessage();
        }
    }

    /** Route a consequential connector action through the Approval Center (bell). */
    private String gated(String connector, String action, String args, RiskLevel risk) {
        String type = "connector:" + connector + ":" + action;   // stable → "remember this decision" works per action
        String title = "Connector " + connector + "." + action;
        ApprovalResult res = approval.submit(type, title,
                "Jarvis wants to run " + connector + "." + action + " (a real external action).",
                risk, args, () -> registry.invoke(connector, action, args));
        ApprovalStatus status = res.request().getStatus();
        if (status == ApprovalStatus.APPROVED) {
            // Auto-approved by a remembered decision — the action already ran.
            Object out = res.result();
            return out == null ? "✅ " + title + " ran (remembered approval)." : String.valueOf(out);
        }
        if (status == ApprovalStatus.DENIED) {
            return "🚫 " + title + " was declined (remembered decision).";
        }
        return "⏸ " + title + " needs your approval — I've sent it to your notification bell with Approve/Decline. "
                + "It will run as soon as you approve.";
    }
}
