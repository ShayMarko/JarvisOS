package com.jarvis.sandbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.config.JarvisLimitsProperties;
import com.jarvis.security.RestrictionPolicy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * The Sandbox Runtime (spec §11.4). Runs a command in an isolated, throwaway
 * working directory with a timeout and captured output, so generated or risky
 * commands can run without touching the user's real working tree.
 *
 * <p>This is a lightweight sandbox: a fresh temp cwd + timeout + output capture,
 * NOT full OS/container isolation. Container-grade isolation (and two-stage
 * "promote after approval") is a later hardening step (Phase 11). The strong
 * safety property today is that nothing here runs until the Approval Center
 * has cleared it.
 */
@Service
@RequiredArgsConstructor
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    private final JarvisFileSystemProperties fsProps;
    private final JarvisLimitsProperties limits;
    private final RestrictionPolicy policy;
    private Path sandboxRoot;

    @PostConstruct
    void init() {
        sandboxRoot = Path.of(fsProps.getJarvisRoot()).toAbsolutePath().normalize().resolve(".sandbox");
        try {
            Files.createDirectories(sandboxRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create sandbox root", e);
        }
    }

    public SandboxResult run(String command, int timeoutSeconds) {
        // Hard floor: a policy-denied command never runs, regardless of approval.
        policy.assertCommandAllowed(command);
        long start = System.nanoTime();
        Path workdir = createWorkdir();
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(workdir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> readInto(process, output), "sandbox-reader");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            boolean timedOut = !finished;
            if (timedOut) {
                process.destroyForcibly();
            }
            reader.join(1000);
            int exit = finished ? process.exitValue() : -1;
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new SandboxResult(exit, output.toString(), ms, timedOut, workdir.getFileName().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Sandbox execution failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sandbox execution interrupted", e);
        } finally {
            deleteQuietly(workdir);
        }
    }

    public Path getSandboxRoot() {
        return sandboxRoot;
    }

    private Path createWorkdir() {
        try {
            return Files.createTempDirectory(sandboxRoot, "run-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create sandbox workdir", e);
        }
    }

    private void readInto(Process process, StringBuilder sink) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && sink.length() < limits.getSandboxMaxOutputChars()) {
                sink.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // process ended / stream closed
        }
    }

    private void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException e) {
            log.debug("Could not clean sandbox workdir {}", dir, e);
        }
    }
}
