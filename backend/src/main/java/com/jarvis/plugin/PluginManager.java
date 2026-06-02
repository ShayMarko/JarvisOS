package com.jarvis.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.agent.AgentRegistry;
import com.jarvis.ai.tools.Tool;
import com.jarvis.ai.tools.ToolRegistry;
import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.error.Exceptions.ValidationException;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.plugin.spi.JarvisPlugin;

import lombok.RequiredArgsConstructor;

/**
 * Dynamic plugin loader (spec §8). Scans a plugins folder for {@code *.jar} files,
 * loads each in its own {@link URLClassLoader}, discovers {@link JarvisPlugin}
 * implementations via {@link ServiceLoader}, registers their tools into the live
 * {@link ToolRegistry}, and grants them to the General agent — so a dropped-in JAR
 * adds capabilities without a rebuild. Also backs the local "marketplace": a
 * {@code catalog.json} of available plugins that can be installed (real JAR copy +
 * hot load) and uninstalled (unregister tools + close class loader + delete JAR).
 */
@Service
@RequiredArgsConstructor
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final JarvisPluginProperties props;
    private final FileSystemService fs;
    private final ToolRegistry tools;
    private final AgentRegistry agents;
    private final AuditService audit;
    private final ObjectMapper mapper;

    /** Public view of a loaded plugin. */
    public record PluginInfo(String id, String name, String version, String description,
                             String jar, List<String> tools) {}

    /** A catalog entry: an installable plugin. */
    public record CatalogEntry(String id, String name, String description, String version,
                               String jar, boolean installed) {}

    private final Map<String, PluginInfo> loaded = new LinkedHashMap<>();
    private final Map<String, URLClassLoader> loaders = new LinkedHashMap<>();

    @PostConstruct
    public synchronized void init() {
        if (!props.isEnabled()) {
            log.info("Plugin loader disabled (jarvis.plugins.enabled=false).");
            return;
        }
        Path dir = pluginsDir();
        try (Stream<Path> jars = Files.list(dir)) {
            List<Path> files = jars.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
            for (Path jar : files) {
                try {
                    loadJar(jar);
                } catch (Exception e) {
                    log.warn("Failed to load plugin {}: {}", jar.getFileName(), e.getMessage());
                }
            }
            log.info("Plugin loader: {} plugin(s) from {}", loaded.size(), dir);
        } catch (IOException e) {
            log.warn("Could not scan plugins folder {}: {}", dir, e.getMessage());
        }
    }

    public synchronized List<PluginInfo> installed() {
        return List.copyOf(loaded.values());
    }

    /** The marketplace catalog, with each entry flagged installed/not. */
    public synchronized List<CatalogEntry> catalog() {
        Path file = catalogFile();
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            CatalogEntry[] raw = mapper.readValue(Files.readAllBytes(file), CatalogEntry[].class);
            List<CatalogEntry> out = new ArrayList<>();
            for (CatalogEntry e : raw) {
                out.add(new CatalogEntry(e.id(), e.name(), e.description(), e.version(), e.jar(),
                        loaded.containsKey(e.id())));
            }
            return out;
        } catch (IOException e) {
            log.warn("Could not read plugin catalog {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    /** Install a plugin named in the catalog (copies its JAR into the plugins dir + hot-loads). */
    public synchronized List<PluginInfo> installFromCatalog(String id) {
        CatalogEntry entry = catalog().stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("No catalog plugin with id '" + id + "'."));
        Path src = resolveCatalogJar(entry.jar());
        return installFromPath(src.toString());
    }

    /** Install a plugin from a JAR on disk (copies it into the plugins dir + hot-loads). */
    public synchronized List<PluginInfo> installFromPath(String pathStr) {
        Path src = Paths.get(pathStr).toAbsolutePath().normalize();
        if (!Files.exists(src) || !src.toString().endsWith(".jar")) {
            throw new ValidationException("Not a JAR file: " + pathStr);
        }
        Path dest = pluginsDir().resolve(src.getFileName().toString());
        try {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ValidationException("Could not copy plugin JAR: " + e.getMessage());
        }
        List<PluginInfo> added = loadJar(dest);
        audit.record("PLUGIN", "plugin:install", dest.getFileName().toString(), "OK",
                added.stream().map(PluginInfo::id).toList().toString());
        return added;
    }

    /** Uninstall: unregister its tools, close its class loader, and delete the JAR. */
    public synchronized String uninstall(String id) {
        PluginInfo info = loaded.get(id);
        if (info == null) {
            throw new NotFoundException("No installed plugin with id '" + id + "'.");
        }
        info.tools().forEach(tools::unregister);
        agents.revokeToolsFromGeneral(info.tools());
        URLClassLoader loader = loaders.remove(id);
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException e) {
                log.warn("Closing class loader for {} failed: {}", id, e.getMessage());
            }
        }
        try {
            Files.deleteIfExists(pluginsDir().resolve(info.jar()));
        } catch (IOException e) {
            log.warn("Could not delete plugin JAR {}: {}", info.jar(), e.getMessage());
        }
        loaded.remove(id);
        audit.record("PLUGIN", "plugin:uninstall", id, "OK", info.tools().toString());
        return "Uninstalled plugin '" + info.name() + "' (" + info.tools().size() + " tool(s) removed).";
    }

    // --- internals ----------------------------------------------------------

    private List<PluginInfo> loadJar(Path jar) {
        List<PluginInfo> added = new ArrayList<>();
        URLClassLoader loader;
        try {
            loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, getClass().getClassLoader());
        } catch (Exception e) {
            throw new ValidationException("Bad plugin JAR URL: " + e.getMessage());
        }
        boolean anyFound = false;
        try {
            ServiceLoader<JarvisPlugin> sl = ServiceLoader.load(JarvisPlugin.class, loader);
            for (JarvisPlugin plugin : sl) {
                anyFound = true;
                if (loaded.containsKey(plugin.id())) {
                    log.warn("Plugin '{}' already loaded — skipping duplicate from {}", plugin.id(), jar.getFileName());
                    continue;
                }
                List<String> names = new ArrayList<>();
                for (Tool t : plugin.tools()) {
                    if (tools.register(t)) {
                        names.add(t.spec().name());
                    } else {
                        log.warn("Plugin '{}' tool '{}' clashes with an existing tool — skipped.",
                                plugin.id(), t.spec().name());
                    }
                }
                agents.grantToolsToGeneral(names);
                PluginInfo info = new PluginInfo(plugin.id(), plugin.name(), plugin.version(),
                        plugin.description(), jar.getFileName().toString(), names);
                loaded.put(plugin.id(), info);
                loaders.put(plugin.id(), loader);
                added.add(info);
                log.info("Loaded plugin '{}' v{} ({} tool(s): {})", plugin.id(), plugin.version(), names.size(), names);
            }
        } catch (Throwable t) {  // a malformed plugin must not crash startup
            log.warn("Error loading plugins from {}: {}", jar.getFileName(), t.toString());
        }
        if (!anyFound) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // nothing to clean
            }
            throw new ValidationException("No JarvisPlugin service found in " + jar.getFileName()
                    + " (missing META-INF/services entry?).");
        }
        return added;
    }

    private Path pluginsDir() {
        Path dir = props.getDir().isBlank()
                ? fs.getRoot().resolve("Plugins")
                : Paths.get(props.getDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ValidationException("Could not create plugins folder: " + e.getMessage());
        }
        return dir;
    }

    private Path catalogFile() {
        return props.getCatalog().isBlank()
                ? pluginsDir().resolve("catalog.json")
                : Paths.get(props.getCatalog());
    }

    /** A catalog jar path may be absolute or relative to the plugins dir. */
    private Path resolveCatalogJar(String jar) {
        Path p = Paths.get(jar);
        return p.isAbsolute() ? p : pluginsDir().resolve(jar);
    }
}
