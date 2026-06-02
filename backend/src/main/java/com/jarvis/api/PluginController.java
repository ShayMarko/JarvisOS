package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.plugin.PluginManager;
import com.jarvis.plugin.PluginManager.CatalogEntry;
import com.jarvis.plugin.PluginManager.PluginInfo;
import com.jarvis.plugin.PluginRegistry;

/** Plugin / Extension surface + marketplace (spec §8). */
@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
public class PluginController {

    public record InstallRequest(String id, String path) {}

    private final PluginRegistry plugins;
    private final PluginManager manager;

    /** The whole extension surface (commands/tools/connectors/agents + installed plugins). */
    @GetMapping
    public Map<String, Object> surface() {
        return plugins.surface();
    }

    /** Installed dynamic plugins. */
    @GetMapping("/installed")
    public List<PluginInfo> installed() {
        return manager.installed();
    }

    /** The marketplace catalog of available plugins, each flagged installed/not. */
    @GetMapping("/catalog")
    public List<CatalogEntry> catalog() {
        return manager.catalog();
    }

    /** Install by catalog id, or by an on-disk JAR path. Hot-loads the contributed tools. */
    @PostMapping("/install")
    public List<PluginInfo> install(@RequestBody InstallRequest req) {
        if (req.id() != null && !req.id().isBlank()) {
            return manager.installFromCatalog(req.id());
        }
        return manager.installFromPath(req.path());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> uninstall(@PathVariable String id) {
        return Map.of("message", manager.uninstall(id));
    }
}
