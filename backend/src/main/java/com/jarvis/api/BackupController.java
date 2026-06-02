package com.jarvis.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.backup.BackupService;
import com.jarvis.backup.BackupService.BackupInfo;

import lombok.RequiredArgsConstructor;

/** REST surface for the HUD's Backups window: list, create, restore Explorer snapshots. */
@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backup;

    @GetMapping
    public List<BackupInfo> list() {
        return backup.list();
    }

    @PostMapping
    public BackupInfo create() {
        return backup.create();
    }

    @PostMapping("/{name}/restore")
    public Map<String, String> restore(@PathVariable String name) {
        return Map.of("message", backup.restore(name));
    }
}
