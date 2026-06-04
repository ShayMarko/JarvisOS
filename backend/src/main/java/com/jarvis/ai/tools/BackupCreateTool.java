package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.backup.BackupService;
import com.jarvis.backup.BackupService.BackupInfo;

import lombok.RequiredArgsConstructor;

/** Creates a timestamped ZIP snapshot of the whole Jarvis Explorer (optionally encrypted for off-box sync). */
@Component
@RequiredArgsConstructor
public class BackupCreateTool implements Tool {

    private final BackupService backup;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("backup_create",
                "Create a timestamped ZIP snapshot of the entire Jarvis Explorer. Set 'encrypted' true to write an "
                + "AES-encrypted .zip.enc to the cloud-sync folder (safe to keep off-box). Returns the backup name and size.",
                "{\"type\":\"object\",\"properties\":{\"encrypted\":{\"type\":\"boolean\"}}}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String args) {
        boolean encrypted = "true".equalsIgnoreCase(ToolArgs.firstStr(mapper, args, "encrypted", "encrypt", "secure"));
        try {
            BackupInfo info = encrypted ? backup.createEncrypted() : backup.create();
            return (encrypted ? "Encrypted backup created: " : "Backup created: ")
                    + info.name() + " (" + (info.sizeBytes() / 1024) + " KB).";
        } catch (Exception e) {
            return "Error creating backup: " + e.getMessage();
        }
    }
}
