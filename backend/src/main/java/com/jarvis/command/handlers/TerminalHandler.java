package com.jarvis.command.handlers;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.approval.ApprovalResult;
import com.jarvis.approval.ApprovalService;
import com.jarvis.approval.ApprovalStatus;
import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.config.JarvisLimitsProperties;
import com.jarvis.sandbox.SandboxService;
import com.jarvis.security.RiskClassifier;
import com.jarvis.security.RiskLevel;

import lombok.RequiredArgsConstructor;

/**
 * {@code /terminal <command>} — classifies the command's risk, requires human
 * approval, then runs it in the Sandbox (spec §5.2, §11.2, §11.4, Appendix A).
 */
@Component
@RequiredArgsConstructor
public class TerminalHandler implements CommandHandler {

    private final RiskClassifier classifier;
    private final ApprovalService approval;
    private final SandboxService sandbox;
    private final JarvisLimitsProperties limits;

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("terminal", "/terminal", List.of("run command", "execute"),
                "Run a terminal command (risk-classified, approved, sandboxed).",
                List.of("command"), List.of("terminal:execute"), true, CommandCategory.SYSTEM);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        String command = context.argLine().trim();
        if (command.isEmpty()) {
            return CommandResult.error("Usage: /terminal <command>");
        }
        RiskLevel risk = classifier.classify(command);
        ApprovalResult outcome = approval.submit(
                "terminal",
                "Run: " + command,
                "Execute a shell command inside the Jarvis sandbox.",
                risk,
                command,
                () -> sandbox.run(command, limits.getSandboxTimeoutSeconds()));

        ApprovalStatus status = outcome.request().getStatus();
        return switch (status) {
            case PENDING -> CommandResult.ok("approval-pending",
                    "Approval required (risk " + risk + "). Open /approve to decide.",
                    outcome.request());
            case DENIED -> CommandResult.error("Denied by a remembered decision.");
            case APPROVED -> CommandResult.ok("sandbox", "Sandbox output", outcome.result());
        };
    }
}
