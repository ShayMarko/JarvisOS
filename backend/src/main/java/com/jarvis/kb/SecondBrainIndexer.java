package com.jarvis.kb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jarvis.explorer.FileSystemService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * The "second brain" — a background sweep that keeps the Knowledge Base in continuous sync with the
 * user's notes and documents. It is NOT a question-answerer; it's the librarian that keeps the shelves
 * current so that when the model reaches for {@code kb_search}, everything the user wrote is already
 * there and fresh.
 *
 * <p>Smart-and-automatic, not a dumb cron: it compares each file's last-modified time against when the
 * KB last indexed it and re-indexes ONLY what actually changed (new or edited), prunes docs whose
 * source file vanished, and caps work per sweep so a large folder never stalls a tick.
 */
@Service
@RequiredArgsConstructor
public class SecondBrainIndexer {

    private static final Logger log = LoggerFactory.getLogger(SecondBrainIndexer.class);

    private final KnowledgeBaseService kb;
    private final FileSystemService fileSystem;
    private final JarvisSecondBrainProperties props;

    @PostConstruct
    void init() {
        log.info("Second-brain auto-index {} (every {} ms, folders {}).",
                props.isEnabled() ? "on" : "off", props.getIntervalMs(), props.getFolders());
    }

    @Scheduled(fixedDelayString = "${jarvis.second-brain.interval-ms:900000}", initialDelay = 90_000)
    void sweep() {
        if (!props.isEnabled()) {
            return;
        }
        try {
            int[] r = indexOnce();
            if (r[0] > 0 || r[1] > 0) {
                log.info("Second-brain sweep: indexed {} new/changed, pruned {} removed.", r[0], r[1]);
            }
        } catch (RuntimeException e) {
            log.debug("Second-brain sweep failed: {}", e.getMessage());
        }
    }

    /**
     * One full reconciliation pass. Returns {@code [indexedCount, prunedCount]}. Package-private so a
     * test can drive it deterministically without the scheduler.
     */
    int[] indexOnce() {
        Path root = fileSystem.getRoot();

        // What the KB already knows: source path -> when it was last indexed.
        Map<String, Instant> indexed = new HashMap<>();
        for (KbDocument d : kb.documents()) {
            indexed.put(d.getSource(), d.getCreatedAt());
        }

        Set<String> seenOnDisk = new HashSet<>();
        List<String> toIndex = new ArrayList<>();

        for (String folder : props.getFolders()) {
            Path dir = root.resolve(folder);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir, Math.max(1, props.getMaxDepth()))) {
                walk.filter(Files::isRegularFile)
                        .filter(this::indexable)
                        .forEach(p -> {
                            String rel = relativize(root, p);
                            seenOnDisk.add(rel);
                            Instant indexedAt = indexed.get(rel);
                            if (indexedAt == null || lastModified(p).isAfter(indexedAt)) {
                                toIndex.add(rel);
                            }
                        });
            } catch (Exception ignored) {
                // folder unreadable — skip it this sweep
            }
        }

        int indexedCount = 0;
        for (String rel : toIndex) {
            if (indexedCount >= props.getMaxFilesPerTick()) {
                break;   // leave the rest for the next sweep
            }
            try {
                kb.indexFile(rel);
                indexedCount++;
            } catch (Exception e) {
                log.debug("skip {}: {}", rel, e.getMessage());
            }
        }

        int prunedCount = 0;
        if (props.isPruneDeleted()) {
            for (KbDocument d : kb.documents()) {
                String src = d.getSource();
                if (underWatchedFolder(src) && !seenOnDisk.contains(src)) {
                    try {
                        kb.delete(d.getId());
                        prunedCount++;
                    } catch (Exception ignored) {
                        // already gone — fine
                    }
                }
            }
        }
        return new int[]{indexedCount, prunedCount};
    }

    private boolean indexable(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        if (name.startsWith(".")) {
            return false;   // skip dotfiles (.DS_Store, etc.)
        }
        boolean extOk = props.getExtensions().stream().anyMatch(name::endsWith);
        if (!extOk) {
            return false;
        }
        try {
            return Files.size(p) <= props.getMaxFileBytes();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean underWatchedFolder(String source) {
        for (String folder : props.getFolders()) {
            if (source.equals(folder) || source.startsWith(folder + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static Instant lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }
}
