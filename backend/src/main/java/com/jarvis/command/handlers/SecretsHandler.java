package com.jarvis.command.handlers;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.secrets.SecretsVaultService;

/** {@code /secrets} — opens the Secrets Vault (masked) (spec §5.2, §11.3). */
@Component
public class SecretsHandler implements CommandHandler {

    private final SecretsVaultService vault;

    public SecretsHandler(SecretsVaultService vault) {
        this.vault = vault;
    }

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("secrets", "/secrets", List.of("open vault", "credentials"),
                "Open the Secrets Vault.", List.of(), List.of(), true, CommandCategory.SECURITY);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        return CommandResult.ok("secrets", "Secrets Vault", vault.list());
    }
}
