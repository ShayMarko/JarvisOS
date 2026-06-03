package com.jarvis.kb;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.second-brain} — the auto-indexer that keeps the Knowledge Base continuously
 * in sync with the user's notes/documents. It's a behind-the-scenes capability: it doesn't answer
 * questions itself, it just guarantees that when the model reaches for {@code kb_search} the recall
 * is complete and current. Smart-and-automatic: it re-indexes only what actually changed.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.second-brain")
public class JarvisSecondBrainProperties {

    private boolean enabled = true;
    /** How often the indexer sweeps (ms). */
    private long intervalMs = 900_000;   // 15 min
    /** Jarvis-root subfolders to keep indexed (walked recursively). */
    private List<String> folders = List.of("Documents", "Notes");
    /** Text-like extensions worth indexing (lower-case, with dot). */
    private List<String> extensions = List.of(
            ".txt", ".md", ".markdown", ".text", ".log", ".csv", ".json", ".yml", ".yaml", ".rtf");
    /** Skip files larger than this (bytes) — they're rarely prose worth chunking. */
    private long maxFileBytes = 1_000_000;
    /** Index at most this many files per sweep, so a big folder doesn't stall a tick. */
    private int maxFilesPerTick = 25;
    /** How deep to recurse under each folder. */
    private int maxDepth = 4;
    /** Drop KB docs whose source file disappeared (only for files under the watched folders). */
    private boolean pruneDeleted = true;
}
