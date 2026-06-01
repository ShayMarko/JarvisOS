package com.jarvis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.config.JarvisSecurityProperties;
import com.jarvis.error.Exceptions.ApprovalRequiredException;
import com.jarvis.error.Exceptions.PathBlockedException;
import com.jarvis.error.Exceptions.PermissionDeniedException;

class PermissionGuardTest {

    @TempDir
    Path root;

    private final JarvisFileSystemProperties fsProps = new JarvisFileSystemProperties();
    private final JarvisSecurityProperties secProps = new JarvisSecurityProperties();

    private PermissionGuard guard(PermissionMode mode) {
        fsProps.setJarvisRoot(root.toString());
        fsProps.setBlockedPaths(java.util.List.of(root.resolve("secret").toString()));
        secProps.setPermissionMode(mode);
        PermissionGuard g = new PermissionGuard(fsProps, secProps);
        g.init();
        return g;
    }

    @BeforeEach
    void reset() {
        fsProps.setAllowedFolders(java.util.List.of());
    }

    @Test
    void allowsReadWithinRoot() {
        assertThatCode(() -> guard(PermissionMode.ASSISTED).check(root.resolve("a.txt"), Operation.READ, false))
                .doesNotThrowAnyException();
    }

    @Test
    void safeModeBlocksMutations() {
        assertThatThrownBy(() -> guard(PermissionMode.SAFE).check(root.resolve("a.txt"), Operation.WRITE, false))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void assistedModeRequiresConfirmationToDelete() {
        PermissionGuard g = guard(PermissionMode.ASSISTED);
        assertThatThrownBy(() -> g.check(root.resolve("a.txt"), Operation.DELETE, false))
                .isInstanceOf(ApprovalRequiredException.class);
        assertThatCode(() -> g.check(root.resolve("a.txt"), Operation.DELETE, true))
                .doesNotThrowAnyException();
    }

    @Test
    void autonomousModeDeletesWithoutConfirmation() {
        assertThatCode(() -> guard(PermissionMode.AUTONOMOUS).check(root.resolve("a.txt"), Operation.DELETE, false))
                .doesNotThrowAnyException();
    }

    @Test
    void blockedPathIsRejected() {
        assertThatThrownBy(() -> guard(PermissionMode.ASSISTED).check(root.resolve("secret/x"), Operation.READ, false))
                .isInstanceOf(PathBlockedException.class);
    }

    @Test
    void pathOutsideAnyScopeIsDenied() {
        assertThatThrownBy(() -> guard(PermissionMode.ASSISTED).check(Path.of("/tmp/elsewhere/x"), Operation.READ, false))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void modeIsReported() {
        assertThat(guard(PermissionMode.SAFE).mode()).isEqualTo(PermissionMode.SAFE);
    }
}
