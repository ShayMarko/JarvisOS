package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.approval.ApprovalRequest;
import com.jarvis.approval.ApprovalService;
import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;

/**
 * {@code /approve} — list pending approvals, or {@code /approve <id>} to approve one. Works from the HUD,
 * the command palette, and the Discord/Telegram control channel, so you can decide remotely (spec §5.2, §11.2).
 */
@Component
@RequiredArgsConstructor
public class ApproveHandler implements CommandHandler {

    private final ApprovalService approval;


    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("approve", "/approve", List.of("approvals", "approval center"),
                "List pending approvals, or /approve <id> to approve one.", List.of(), List.of(), true, CommandCategory.SECURITY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String id = context.args().isEmpty() ? null : context.args().get(0).trim();
        if (id == null || id.isBlank()) {
            return pendingSummary(approval.pending());
        }
        try {
            ApprovalRequest req = approval.approve(id, false).request();
            String summary = req.getResultSummary();
            return CommandResult.ok("approvals",
                    "✅ Approved: " + req.getTitle()
                            + (summary == null || summary.isBlank() ? "" : "\n" + summary),
                    approval.pending());
        } catch (RuntimeException e) {
            return CommandResult.error(e.getMessage());
        }
    }

    /** Shared, chat/Discord-friendly rendering of the pending queue with ids + reply instructions. */
    static CommandResult pendingSummary(List<ApprovalRequest> pending) {
        if (pending.isEmpty()) {
            return CommandResult.ok("approvals", "✅ Nothing waiting — you're all clear.", pending);
        }
        StringBuilder sb = new StringBuilder("🔐 " + pending.size() + " awaiting your decision:\n");
        for (ApprovalRequest a : pending) {
            sb.append("• [").append(a.getId()).append("] ").append(a.getTitle())
                    .append(" — risk ").append(a.getRiskLevel()).append('\n');
        }
        sb.append("\nReply  `/approve <id>`  to approve, or  `/decline <id>`  to decline.");
        return CommandResult.ok("approvals", sb.toString(), pending);
    }
}
