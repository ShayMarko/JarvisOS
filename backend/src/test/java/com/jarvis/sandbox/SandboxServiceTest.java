package com.jarvis.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.security.JarvisPolicyProperties;
import com.jarvis.security.RestrictionPolicy;

class SandboxServiceTest {

    @TempDir
    Path root;

    private SandboxService sandbox;

    @BeforeEach
    void setUp() {
        JarvisFileSystemProperties props = new JarvisFileSystemProperties();
        props.setJarvisRoot(root.toString());
        sandbox = new SandboxService(props, new com.jarvis.config.JarvisLimitsProperties(),
                new RestrictionPolicy(new JarvisPolicyProperties()));
        sandbox.init();
    }

    @Test
    void capturesOutputAndExitCode() {
        SandboxResult result = sandbox.run("echo hello-sandbox", 10);
        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("hello-sandbox");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    void reportsNonZeroExit() {
        SandboxResult result = sandbox.run("exit 3", 10);
        assertThat(result.exitCode()).isEqualTo(3);
    }

    @Test
    void timesOutLongCommands() {
        SandboxResult result = sandbox.run("sleep 5", 1);
        assertThat(result.timedOut()).isTrue();
    }

    @Test
    void runsInsideAnExistingProjectDirAndLeavesItInPlace() throws Exception {
        // A built "project" with a file the command reads — proves runIn executes in THAT dir.
        Path project = root.resolve("Projects/demo");
        java.nio.file.Files.createDirectories(project);
        java.nio.file.Files.writeString(project.resolve("hello.txt"), "from-project");

        SandboxResult result = sandbox.runIn(project, "cat hello.txt", 10);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("from-project");
        assertThat(java.nio.file.Files.exists(project)).isTrue();   // not deleted afterward
    }

    @Test
    void rejectsAMissingRunDirectory() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> sandbox.runIn(root.resolve("Projects/does-not-exist"), "echo x", 10));
    }
}
