package com.jarvis.security;

import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.config.JarvisFileSystemProperties.AllowedFolder;
import com.jarvis.config.JarvisFileSystemProperties.Permission;
import com.jarvis.config.JarvisSecurityProperties;
import com.jarvis.error.Exceptions.ApprovalRequiredException;
import com.jarvis.error.Exceptions.PathBlockedException;
import com.jarvis.error.Exceptions.PermissionDeniedException;

import jakarta.annotation.PostConstruct;

/**
 * Central authority for "may this operation touch this path?" (spec §11, Appendix A).
 * Resolves the path to one of the permitted scopes (the Jarvis root, which is
 * read-write, or a configured allowed folder), rejects blocked paths, and
 * applies the active {@link PermissionMode}.
 */
@Component
@RequiredArgsConstructor
public class PermissionGuard {

    /** A real location Jarvis may touch, with its permission level. */
    private record Scope(Path root, Permission permission) {}

    private final JarvisFileSystemProperties fsProps;
    private final JarvisSecurityProperties securityProps;
    private List<Scope> scopes;
    private List<Path> blocked;


    @PostConstruct
    public void init() {
        List<Scope> built = new ArrayList<>();
        // The Jarvis Explorer root is always read-write — it is Jarvis's own home.
        built.add(new Scope(abs(fsProps.getJarvisRoot()), Permission.READ_WRITE));
        for (AllowedFolder folder : fsProps.getAllowedFolders()) {
            built.add(new Scope(abs(folder.getPath()), folder.getPermission()));
        }
        this.scopes = List.copyOf(built);
        this.blocked = fsProps.getBlockedPaths().stream().map(PermissionGuard::abs).toList();
    }

    public PermissionMode mode() {
        return securityProps.getPermissionMode();
    }

    /**
     * Throws if {@code target} may not be used for {@code op}. {@code confirmed}
     * stands in for an Approval Center decision (Phase 5).
     */
    public void check(Path target, Operation op, boolean confirmed) {
        Path path = target.toAbsolutePath().normalize();

        if (isBlocked(path)) {
            throw new PathBlockedException("Path is blocked: " + path);
        }

        Scope scope = scopeFor(path);
        if (scope == null) {
            throw new PermissionDeniedException("Path is outside any allowed folder: " + path);
        }

        if (op.isMutating() && scope.permission() == Permission.READ_ONLY) {
            throw new PermissionDeniedException("Folder is read-only: " + scope.root());
        }

        switch (mode()) {
            case SAFE -> {
                if (op.isMutating()) {
                    throw new PermissionDeniedException("Safe Mode is read-only; " + op + " is not allowed.");
                }
            }
            case ASSISTED -> {
                if (op == Operation.DELETE && !confirmed) {
                    throw new ApprovalRequiredException("Deleting requires confirmation.");
                }
            }
            case AUTONOMOUS -> { /* mutations permitted within scope */ }
        }
    }

    private boolean isBlocked(Path path) {
        return blocked.stream().anyMatch(path::startsWith);
    }

    private Scope scopeFor(Path path) {
        return scopes.stream().filter(s -> path.startsWith(s.root())).findFirst().orElse(null);
    }

    private static Path abs(String raw) {
        String expanded = raw.startsWith("~")
                ? System.getProperty("user.home") + raw.substring(1)
                : raw;
        return Path.of(expanded).toAbsolutePath().normalize();
    }
}
