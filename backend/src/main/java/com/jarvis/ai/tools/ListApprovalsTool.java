package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.approval.ApprovalRequest;
import com.jarvis.approval.ApprovalService;

import lombok.RequiredArgsConstructor;

/** Lists actions waiting on the user's approval — so "what's pending my approval" works anywhere. */
@Component
@RequiredArgsConstructor
public class ListApprovalsTool implements Tool {

    private final ApprovalService approvals;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("list_approvals",
                "List actions waiting on the user's approval (risk, what they'd do). Use when asked "
                + "'what's pending', 'anything waiting on me', 'what needs my approval'.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String argumentsJson) {
        List<ApprovalRequest> pending = approvals.pending();
        if (pending.isEmpty()) {
            return "Nothing is waiting on your approval — Jarvis is clear to act.";
        }
        StringBuilder sb = new StringBuilder("⏳ Awaiting your approval (" + pending.size() + "):\n");
        for (ApprovalRequest a : pending) {
            sb.append("• [").append(a.getRiskLevel()).append("] ").append(a.getTitle());
            if (a.getDescription() != null && !a.getDescription().isBlank()) {
                sb.append(" — ").append(a.getDescription());
            }
            sb.append('\n');
        }
        sb.append("Approve or decline them in the notification bell / Approval Center.");
        return sb.toString().strip();
    }
}
