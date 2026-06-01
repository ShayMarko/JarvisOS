package com.jarvis.command.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.config.JarvisSecurityProperties;

/** {@code /settings} — shows the current Jarvis configuration (spec §5.2). */
@Component
public class SettingsHandler implements CommandHandler {

    private final JarvisFileSystemProperties filesystem;
    private final JarvisSecurityProperties security;

    public SettingsHandler(JarvisFileSystemProperties filesystem, JarvisSecurityProperties security) {
        this.filesystem = filesystem;
        this.security = security;
    }

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("settings", "/settings",
                "Open Jarvis settings.", CommandCategory.SYSTEM);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        Map<String, Object> fs = new LinkedHashMap<>();
        fs.put("jarvisRoot", filesystem.getJarvisRoot());
        fs.put("explorerFolders", filesystem.getExplorerFolders());
        fs.put("allowedFolders", filesystem.getAllowedFolders().stream()
                .map(f -> Map.of("name", f.getName(), "path", f.getPath(),
                        "permission", f.getPermission().name()))
                .toList());
        fs.put("blockedPaths", filesystem.getBlockedPaths());

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("filesystem", fs);
        settings.put("permissionMode", security.getPermissionMode().name()); // spec §11.1
        settings.put("phase", 2);
        return CommandResult.ok("settings", "Jarvis settings", settings);
    }
}
