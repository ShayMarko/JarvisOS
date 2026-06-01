package com.jarvis.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import com.jarvis.config.JarvisFileSystemProperties;

class SandboxServiceTest {

    @TempDir
    Path root;

    private SandboxService sandbox;

    @BeforeEach
    void setUp() {
        JarvisFileSystemProperties props = new JarvisFileSystemProperties();
        props.setJarvisRoot(root.toString());
        sandbox = new SandboxService(props, new com.jarvis.config.JarvisLimitsProperties());
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
}
