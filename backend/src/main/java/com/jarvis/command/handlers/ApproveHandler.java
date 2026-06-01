package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.approval.ApprovalService;
import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;

/** {@code /approve} — opens the Approval Center (pending actions) (spec §5.2, §11.2). */
@Component
@RequiredArgsConstructor
public class ApproveHandler implements CommandHandler {

    private final ApprovalService approval;


    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("approve", "/approve", List.of("approvals", "approval center"),
                "Open the Approval Center.", List.of(), List.of(), true, CommandCategory.SECURITY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.ok("approvals", "Approval Center", approval.pending());
    }
}
