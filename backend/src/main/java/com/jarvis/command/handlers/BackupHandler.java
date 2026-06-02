package com.jarvis.command.handlers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.backup.BackupService;
import com.jarvis.backup.BackupService.BackupInfo;
import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;

import lombok.RequiredArgsConstructor;

/**
 * {@code /backup} — snapshot and restore the Jarvis Explorer.
 * <ul>
 *   <li>{@code /backup} — list existing backups</li>
 *   <li>{@code /backup create} — make a new snapshot</li>
 *   <li>{@code /backup restore <name>} — restore from a snapshot (overwrites)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BackupHandler implements CommandHandler {

    private final BackupService backup;

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition("backup", "/backup",
                List.of("backups", "snapshot", "restore backup"),
                "Snapshot or restore the Jarvis Explorer. Use 'create' or 'restore <name>'.",
                List.of(), List.of(), true, CommandCategory.FILES);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        List<String> args = context.args();
        String sub = args.isEmpty() ? "list" : args.get(0).toLowerCase();

        return switch (sub) {
            case "create", "new", "make" -> {
                BackupInfo info = backup.create();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("name", info.name());
                data.put("sizeKB", info.sizeBytes() / 1024);
                data.put("createdAt", info.createdAt());
                yield CommandResult.ok("backup", "Backup created: " + info.name(), data);
            }
            case "restore" -> {
                if (args.size() < 2) {
                    yield CommandResult.ok("backup", "Usage: /backup restore <name>", listData());
                }
                String name = args.get(1);
                yield CommandResult.ok("backup", backup.restore(name), null);
            }
            default -> CommandResult.ok("backup",
                    "Backups (use '/backup create' or '/backup restore <name>'):", listData());
        };
    }

    private Map<String, Object> listData() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> items = backup.list().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", b.name());
            m.put("sizeKB", b.sizeBytes() / 1024);
            m.put("createdAt", b.createdAt());
            return m;
        }).toList();
        data.put("backups", items);
        return data;
    }
}
