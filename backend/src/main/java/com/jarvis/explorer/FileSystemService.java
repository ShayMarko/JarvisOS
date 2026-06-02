package com.jarvis.explorer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.config.JarvisLimitsProperties;
import com.jarvis.error.Exceptions.ConflictException;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.error.Exceptions.PathBlockedException;
import com.jarvis.security.Operation;
import com.jarvis.security.PermissionGuard;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * The File System Capability scoped to the Jarvis Explorer (spec §8, §14).
 *
 * <p>All paths are relative to the Jarvis root; every operation is resolved,
 * checked for traversal, and then authorised by the {@link PermissionGuard}
 * before any I/O happens.
 */
@Service
@RequiredArgsConstructor
public class FileSystemService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemService.class);

    private final JarvisFileSystemProperties properties;
    private final PermissionGuard guard;
    private final JarvisLimitsProperties limits;
    private Path root;
    /** The root plus every configured allowed folder — the absolute paths the Explorer may address. */
    private List<Path> mounts;

    @PostConstruct
    void init() {
        this.root = expandHome(properties.getJarvisRoot());
        List<Path> built = new ArrayList<>();
        built.add(root);
        properties.getAllowedFolders().forEach(f -> built.add(expandHome(f.getPath())));
        this.mounts = List.copyOf(built);
        ensureExplorerLayout();
        log.info("Jarvis Explorer root: {} (+{} allowed folder(s))", root, mounts.size() - 1);
    }

    /** Expand a leading {@code ~} to the user home, then absolutize + normalize. */
    private static Path expandHome(String raw) {
        String expanded = raw != null && raw.startsWith("~")
                ? System.getProperty("user.home") + raw.substring(1)
                : raw;
        return Path.of(expanded).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    /** Resolve a relative Explorer path to a guarded, existing absolute path (for reveal-in-Finder). */
    public Path resolveExisting(String relativePath) {
        Path target = resolveScoped(relativePath);
        guard.check(target, Operation.READ, false);
        if (!Files.exists(target)) {
            throw new NotFoundException("Does not exist: " + relativePath);
        }
        return target;
    }

    private void ensureExplorerLayout() {
        try {
            Files.createDirectories(root);
            for (String folder : properties.getExplorerFolders()) {
                Files.createDirectories(root.resolve(folder));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not initialise Jarvis Explorer layout", e);
        }
    }

    // --- Read -----------------------------------------------------------------

    public List<FileNode> list(String relativePath) {
        Path target = resolveScoped(relativePath);
        guard.check(target, Operation.READ, false);
        if (!Files.isDirectory(target)) {
            throw new NotFoundException("Not a directory: " + relativePath);
        }
        List<FileNode> nodes = new ArrayList<>();
        try (Stream<Path> entries = Files.list(target)) {
            entries.forEach(p -> nodes.add(toNode(p)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list directory: " + relativePath, e);
        }
        nodes.sort(Comparator.comparing(FileNode::directory).reversed()
                .thenComparing(n -> n.name().toLowerCase()));
        // At the Explorer root, surface the configured allowed folders as navigable entries.
        if (target.equals(root)) {
            List<FileNode> withMounts = new ArrayList<>();
            properties.getAllowedFolders().forEach(f -> {
                Path mp = expandHome(f.getPath());
                if (Files.isDirectory(mp)) {
                    withMounts.add(new FileNode("📂 " + f.getName(), mp.toString(), true, 0, Instant.EPOCH));
                }
            });
            withMounts.addAll(nodes);
            return withMounts;
        }
        return nodes;
    }

    public FileContent readText(String relativePath) {
        Path target = resolveScoped(relativePath);
        guard.check(target, Operation.READ, false);
        if (!Files.isRegularFile(target)) {
            throw new NotFoundException("Not a file: " + relativePath);
        }
        try {
            if (Files.size(target) > limits.getFileMaxTextBytes()) {
                throw new ConflictException("File too large to open as text (> "
                        + (limits.getFileMaxTextBytes() / (1024 * 1024)) + " MB): " + relativePath);
            }
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return new FileContent(displayPath(target), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read file: " + relativePath, e);
        }
    }

    public List<FileNode> search(String query, String relativeBase) {
        Path base = resolveScoped(relativeBase);
        guard.check(base, Operation.READ, false);
        String needle = query.toLowerCase();
        List<FileNode> matches = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(p -> !p.equals(base))
                    .filter(p -> p.getFileName().toString().toLowerCase().contains(needle))
                    .limit(limits.getFileSearchMaxResults())
                    .forEach(p -> matches.add(toNode(p)));
        } catch (IOException e) {
            throw new UncheckedIOException("Search failed under: " + relativeBase, e);
        }
        return matches;
    }

    // --- Write ----------------------------------------------------------------

    public FileNode writeText(String relativePath, String content) {
        Path target = resolveScoped(relativePath);
        Operation op = Files.exists(target) ? Operation.WRITE : Operation.CREATE;
        guard.check(target, op, true);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return toNode(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write file: " + relativePath, e);
        }
    }

    public FileNode createDirectory(String relativePath) {
        Path target = resolveScoped(relativePath);
        guard.check(target, Operation.CREATE, true);
        if (Files.exists(target)) {
            throw new ConflictException("Already exists: " + relativePath);
        }
        try {
            Files.createDirectories(target);
            return toNode(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create directory: " + relativePath, e);
        }
    }

    public void delete(String relativePath, boolean confirmed) {
        Path target = resolveScoped(relativePath);
        guard.check(target, Operation.DELETE, confirmed);
        if (!Files.exists(target)) {
            throw new NotFoundException("Does not exist: " + relativePath);
        }
        try {
            // Refuse to delete a non-empty directory in Phase 1/2 — recursive delete is risky.
            if (Files.isDirectory(target)) {
                try (Stream<Path> children = Files.list(target)) {
                    if (children.findAny().isPresent()) {
                        throw new ConflictException("Directory is not empty: " + relativePath);
                    }
                }
            }
            Files.delete(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete: " + relativePath, e);
        }
    }

    // --- Internals ------------------------------------------------------------

    private FileNode toNode(Path p) {
        boolean dir = Files.isDirectory(p);
        long size = 0;
        Instant modified = Instant.EPOCH;
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            size = dir ? 0 : attrs.size();
            modified = attrs.lastModifiedTime().toInstant();
        } catch (IOException ignored) {
            // best effort: surface the entry even if attributes are unreadable
        }
        return new FileNode(p.getFileName().toString(), displayPath(p), dir, size, modified);
    }

    /** Paths under the Jarvis root stay relative ("Notes/x.md"); allowed-folder paths stay absolute. */
    private String displayPath(Path p) {
        return p.startsWith(root) ? root.relativize(p).toString() : p.toString();
    }

    /**
     * Resolve a client path. An absolute path under a known mount (root or an allowed
     * folder) is used as-is; anything else is treated as Explorer-relative and confined
     * to the root. The {@link PermissionGuard} then authorises the actual operation.
     */
    private Path resolveScoped(String path) {
        String clean = path == null ? "" : path.trim();
        if (!clean.isEmpty()) {
            Path abs = Path.of(clean).normalize();
            if (abs.isAbsolute() && mounts.stream().anyMatch(abs::startsWith)) {
                return abs;
            }
        }
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        Path resolved = root.resolve(clean).normalize();
        if (!resolved.startsWith(root)) {
            throw new PathBlockedException("Path escapes the Jarvis Explorer root: " + path);
        }
        return resolved;
    }
}
