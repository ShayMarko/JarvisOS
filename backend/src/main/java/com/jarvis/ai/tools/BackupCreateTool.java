package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.backup.BackupService;
import com.jarvis.backup.BackupService.BackupInfo;

import lombok.RequiredArgsConstructor;

/** Creates a timestamped ZIP snapshot of the whole Jarvis Explorer. */
@Component
@RequiredArgsConstructor
public class BackupCreateTool implements Tool {

    private final BackupService backup;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("backup_create",
                "Create a timestamped ZIP snapshot of the entire Jarvis Explorer. Returns the backup name and size.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String args) {
        try {
            BackupInfo info = backup.create();
            return "Backup created: " + info.name() + " (" + (info.sizeBytes() / 1024) + " KB).";
        } catch (Exception e) {
            return "Error creating backup: " + e.getMessage();
        }
    }
}
