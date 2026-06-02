package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.backup.BackupService;
import com.jarvis.backup.BackupService.BackupInfo;

import lombok.RequiredArgsConstructor;

/** Lists existing Explorer backups (name, size, creation time). */
@Component
@RequiredArgsConstructor
public class BackupListTool implements Tool {

    private final BackupService backup;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("backup_list",
                "List existing Jarvis Explorer backups with their size and creation time.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String args) {
        try {
            List<BackupInfo> backups = backup.list();
            if (backups.isEmpty()) {
                return "No backups yet. Use backup_create to make one.";
            }
            StringBuilder sb = new StringBuilder();
            for (BackupInfo b : backups) {
                sb.append("• ").append(b.name())
                        .append(" (").append(b.sizeBytes() / 1024).append(" KB, ")
                        .append(b.createdAt()).append(")\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error listing backups: " + e.getMessage();
        }
    }
}
