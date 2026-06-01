package com.jarvis.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Project Intelligence (old PRD §6.4): scans the configured developer roots,
 * detects each project's type by its marker file, and resolves which IDE opens it.
 * Read-only — discovery only; opening is a separate, audited action.
 */
@Service
@RequiredArgsConstructor
public class ProjectScanner {

    private static final Logger log = LoggerFactory.getLogger(ProjectScanner.class);

    /** Marker file (or suffix for "*") → project type, in priority order. */
    private static final Map<String, String> MARKERS = new LinkedHashMap<>();
    static {
        MARKERS.put("pom.xml", "maven");
        MARKERS.put("build.gradle", "gradle");
        MARKERS.put("build.gradle.kts", "gradle");
        MARKERS.put("package.json", "node");
        MARKERS.put("Cargo.toml", "rust");
        MARKERS.put("go.mod", "go");
        MARKERS.put("pyproject.toml", "python");
        MARKERS.put("requirements.txt", "python");
        MARKERS.put("*.xcodeproj", "xcode");
        MARKERS.put(".git", "git");
    }

    private final JarvisProjectsProperties props;

    /** Discover all projects under the configured scan roots. */
    public List<ProjectInfo> scan() {
        List<ProjectInfo> found = new ArrayList<>();
        for (String rawRoot : props.getScanRoots()) {
            Path root = expand(rawRoot);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root, Math.max(0, props.getMaxDepth()))) {
                walk.filter(Files::isDirectory)
                        .forEach(dir -> detectType(dir).ifPresent(type ->
                                found.add(toInfo(dir, type))));
            } catch (IOException e) {
                log.debug("Could not scan project root {}", root, e);
            }
        }
        // De-duplicate by path, preferring the first (richer-marker) hit.
        Map<String, ProjectInfo> byPath = new LinkedHashMap<>();
        for (ProjectInfo p : found) {
            byPath.putIfAbsent(p.path(), p);
        }
        return List.copyOf(byPath.values());
    }

    /** Find a project by a fuzzy (case-insensitive substring) name match. */
    public Optional<ProjectInfo> findByName(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String q = query.toLowerCase(Locale.ROOT).strip();
        List<ProjectInfo> all = scan();
        // Prefer an exact name match, then a substring match.
        return all.stream().filter(p -> p.name().equalsIgnoreCase(q)).findFirst()
                .or(() -> all.stream().filter(p -> p.name().toLowerCase(Locale.ROOT).contains(q)).findFirst());
    }

    /** The IDE configured for a given project type, falling back to the default. */
    public String ideFor(String type) {
        return props.getIdeByType().getOrDefault(type, props.getDefaultIde());
    }

    private ProjectInfo toInfo(Path dir, String type) {
        return new ProjectInfo(dir.getFileName().toString(), dir.toString(), type, ideFor(type));
    }

    private Optional<String> detectType(Path dir) {
        for (Map.Entry<String, String> e : MARKERS.entrySet()) {
            String marker = e.getKey();
            if (marker.startsWith("*.")) {
                String suffix = marker.substring(1); // ".xcodeproj"
                if (hasChildWithSuffix(dir, suffix)) {
                    return Optional.of(e.getValue());
                }
            } else if (Files.exists(dir.resolve(marker))) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    private boolean hasChildWithSuffix(Path dir, String suffix) {
        try (Stream<Path> children = Files.list(dir)) {
            return children.anyMatch(c -> c.getFileName().toString().endsWith(suffix));
        } catch (IOException e) {
            return false;
        }
    }

    private static Path expand(String raw) {
        String expanded = raw.startsWith("~")
                ? System.getProperty("user.home") + raw.substring(1)
                : raw;
        return Path.of(expanded).toAbsolutePath().normalize();
    }
}
