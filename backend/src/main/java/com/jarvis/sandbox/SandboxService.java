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
 * <p>Two isolation tiers (chosen at startup):
 * <ul>
 *   <li><b>Container</b> — when {@code jarvis.limits.sandbox-docker=true} and Docker is
 *       available, commands run in a throwaway {@code --network none} container with
 *       memory/CPU caps (added in Phase 5).</li>
 *   <li><b>Lightweight</b> — otherwise, a fresh temp working directory + timeout +
 *       captured output (no OS-level isolation). This is the default.</li>
 * </ul>
 * Either way, the strong safety property is that nothing here runs until the Approval
 * Center has cleared it. Still deferred: the two-stage "promote after approval" flow.
 */
@Service
@RequiredArgsConstructor
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    private final JarvisFileSystemProperties fsProps;
    private final JarvisLimitsProperties limits;
    private final RestrictionPolicy policy;
    private Path sandboxRoot;
    private boolean dockerReady;

    @PostConstruct
    void init() {
        sandboxRoot = Path.of(fsProps.getJarvisRoot()).toAbsolutePath().normalize().resolve(".sandbox");
        try {
            Files.createDirectories(sandboxRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create sandbox root", e);
        }
        dockerReady = limits.isSandboxDocker() && dockerAvailable();
        log.info("Sandbox isolation: {}", dockerReady
                ? "Docker container (" + limits.getSandboxImage() + ", --network none)"
                : "lightweight temp-dir" + (limits.isSandboxDocker() ? " (Docker requested but unavailable)" : ""));
    }

    /** Whether commands run inside an isolated Docker container (vs a temp-dir process). */
    public boolean isContainerized() {
        return dockerReady;
    }

    /** Run a command in a fresh throwaway working directory, deleted afterward. */
    public SandboxResult run(String command, int timeoutSeconds) {
        Path workdir = createWorkdir();
        try {
            return exec(command, workdir, timeoutSeconds);
        } finally {
            deleteQuietly(workdir);
        }
    }

    /**
     * Run a command INSIDE an existing directory (e.g. a built project under {@code Projects/}) and
     * leave it in place — this is how the developer/tester agents build, run and test the app they just
     * generated. Same policy gate, timeout and output capture; same isolation tier as {@link #run}.
     */
    public SandboxResult runIn(Path workdir, String command, int timeoutSeconds) {
        if (workdir == null || !Files.isDirectory(workdir)) {
            throw new IllegalArgumentException("Sandbox run directory does not exist: " + workdir);
        }
        return exec(command, workdir, timeoutSeconds);
    }

    private SandboxResult exec(String command, Path workdir, int timeoutSeconds) {
        // Hard floor: a policy-denied command never runs, regardless of approval.
        policy.assertCommandAllowed(command);
        long start = System.nanoTime();
        try {
            ProcessBuilder pb = new ProcessBuilder(buildCommand(command, workdir));
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
        }
    }

    public Path getSandboxRoot() {
        return sandboxRoot;
    }

    /** Build the OS command: a network-less Docker container when enabled, else a plain shell. */
    private java.util.List<String> buildCommand(String command, Path workdir) {
        if (dockerReady) {
            return java.util.List.of("docker", "run", "--rm",
                    "--network", "none",            // no network egress from sandboxed code
                    "--memory", "256m", "--cpus", "1",
                    "-v", workdir.toAbsolutePath() + ":/work", "-w", "/work",
                    limits.getSandboxImage(), "sh", "-c", command);
        }
        return java.util.List.of("sh", "-c", command);
    }

    private boolean dockerAvailable() {
        try {
            Process p = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true).start();
            return p.waitFor(4, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
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
