package com.jarvis.workflow;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jarvis.explorer.FileSystemService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * Fires FILE_CHANGED workflows when files change in a watched folder (spec §12
 * triggers). The workflow's {@code cron} field holds the folder, relative to the
 * Jarvis Explorer root. Uses the JDK {@link WatchService} on a daemon thread,
 * with a short per-folder debounce so an editor's double-write fires once.
 */
@Component
@RequiredArgsConstructor
public class FileWatchService {

    private static final Logger log = LoggerFactory.getLogger(FileWatchService.class);
    private static final long DEBOUNCE_MS = 1500;

    private final WorkflowRepository workflows;
    private final WorkflowEngine engine;
    private final FileSystemService fs;

    private WatchService watcher;
    private Thread thread;
    private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();
    private final Map<Path, Long> lastFired = new ConcurrentHashMap<>();

    @PostConstruct
    void start() {
        try {
            watcher = FileSystems.getDefault().newWatchService();
        } catch (Exception e) {
            log.warn("File watch unavailable: {}", e.getMessage());
            return;
        }
        thread = new Thread(this::loop, "jarvis-filewatch");
        thread.setDaemon(true);
        thread.start();
        rescan();
    }

    /** (Re)register watches for the current FILE_CHANGED workflows. */
    public synchronized void rescan() {
        if (watcher == null) {
            return;
        }
        // Cancel existing registrations and re-add (workflows may have changed).
        keyToDir.keySet().forEach(WatchKey::cancel);
        keyToDir.clear();
        for (Workflow wf : workflows.findByTriggerTypeAndEnabledTrue(TriggerType.FILE_CHANGED)) {
            String rel = wf.getCron();
            if (rel == null || rel.isBlank()) {
                continue;
            }
            Path dir = fs.getRoot().resolve(rel).normalize();
            if (!Files.isDirectory(dir)) {
                log.info("FILE_CHANGED workflow '{}' watches '{}' which doesn't exist yet — skipping.", wf.getName(), rel);
                continue;
            }
            try {
                WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                keyToDir.put(key, dir);
                log.info("Watching '{}' for FILE_CHANGED workflow '{}'.", rel, wf.getName());
            } catch (Exception e) {
                log.warn("Could not watch '{}': {}", dir, e.getMessage());
            }
        }
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                return;
            }
            Path dir = keyToDir.get(key);
            boolean hadEvents = !key.pollEvents().isEmpty();
            key.reset();
            if (dir != null && hadEvents) {
                fireForDir(dir);
            }
        }
    }

    private void fireForDir(Path dir) {
        long now = System.currentTimeMillis();
        Long last = lastFired.get(dir);
        if (last != null && now - last < DEBOUNCE_MS) {
            return; // coalesce rapid events
        }
        lastFired.put(dir, now);

        String rel = fs.getRoot().relativize(dir).toString();
        List<Workflow> matches = workflows.findByTriggerTypeAndEnabledTrue(TriggerType.FILE_CHANGED).stream()
                .filter(w -> rel.equals(w.getCron() == null ? "" : w.getCron().strip()))
                .toList();
        for (Workflow wf : matches) {
            try {
                engine.start(wf, "file-changed");
                log.info("FILE_CHANGED fired workflow '{}' ({} changed).", wf.getName(), rel);
            } catch (Exception e) {
                log.error("FILE_CHANGED workflow {} failed", wf.getId(), e);
            }
        }
    }

    @PreDestroy
    void stop() {
        if (thread != null) {
            thread.interrupt();
        }
        try {
            if (watcher != null) {
                watcher.close();
            }
        } catch (Exception ignored) {
            // closing
        }
    }
}
