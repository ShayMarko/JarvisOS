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
 * {@code /decline <id>} (alias {@code /deny}) — decline a pending approval by id. The counterpart to
 * {@code /approve}; lets you reject an action from the HUD or the Discord/Telegram control channel.
 */
@Component
@RequiredArgsConstructor
public class DeclineHandler implements CommandHandler {

    private final ApprovalService approval;


    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("decline", "/decline", List.of("deny", "reject"),
                "Decline a pending approval: /decline <id>.", List.of(), List.of(), true, CommandCategory.SECURITY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String id = context.args().isEmpty() ? null : context.args().get(0).trim();
        if (id == null || id.isBlank()) {
            return ApproveHandler.pendingSummary(approval.pending());
        }
        try {
            ApprovalRequest req = approval.deny(id, false);
            return CommandResult.ok("approvals", "🚫 Declined: " + req.getTitle(), approval.pending());
        } catch (RuntimeException e) {
            return CommandResult.error(e.getMessage());
        }
    }
}
