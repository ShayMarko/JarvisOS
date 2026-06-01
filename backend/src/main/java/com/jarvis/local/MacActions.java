package com.jarvis.local;

import lombok.RequiredArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.ConflictException;
import com.jarvis.explorer.FileSystemService;

/**
 * Local Actions / Automation capability for macOS (spec §8). Wraps the native
 * tools — {@code open -a} (launch app), {@code open -R} (reveal in Finder),
 * {@code screencapture}, {@code pbcopy}/{@code pbpaste} (clipboard) and
 * {@code mdfind} (Spotlight) — so the Brain can act on the Mac. These are
 * exposed as agent tools, not hard-coded routes: the agent decides when to use them.
 */
@Service
@RequiredArgsConstructor
public class MacActions {

    private final FileSystemService fileSystem;
    private final AuditService audit;
    private final boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");


    public boolean isMac() {
        return mac;
    }

    public String openApp(String appName) {
        requireMac();
        run(List.of("open", "-a", appName), null);
        audit.record("LOCAL", "open_app", appName, "OK", null);
        return "Opened " + appName + ".";
    }

    /** Open an absolute path with a specific application — e.g. a project folder in an IDE. */
    public String openPathWith(String appName, Path absolutePath) {
        requireMac();
        run(List.of("open", "-a", appName, absolutePath.toString()), null);
        audit.record("LOCAL", "open_project", absolutePath.toString(), "OK", "in " + appName);
        return "Opened " + absolutePath.getFileName() + " in " + appName + ".";
    }

    /** Reveal an absolute path in Finder. */
    public String reveal(Path absolutePath) {
        requireMac();
        run(List.of("open", "-R", absolutePath.toString()), null);
        audit.record("LOCAL", "reveal", absolutePath.toString(), "OK", null);
        return "Revealed in Finder: " + absolutePath;
    }

    public String screenshot(String name) {
        requireMac();
        Path dir = fileSystem.getRoot().resolve("Screenshots");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String file = (name == null || name.isBlank() ? "screenshot" : name.replaceAll("[^a-zA-Z0-9-_]", "_")) + ".png";
        Path target = dir.resolve(file);
        run(List.of("screencapture", "-x", target.toString()), null); // -x = no sound
        audit.record("LOCAL", "screenshot", file, "OK", null);
        return "Saved screenshot to Screenshots/" + file;
    }

    public String clipboardRead() {
        requireMac();
        return run(List.of("pbpaste"), null).trim();
    }

    public String clipboardWrite(String text) {
        requireMac();
        run(List.of("pbcopy"), text);
        audit.record("LOCAL", "clipboard_write", null, "OK", null);
        return "Copied to clipboard.";
    }

    public String say(String text) {
        requireMac();
        run(List.of("say", text), null);
        audit.record("LOCAL", "say", null, "OK", null);
        return "🔊 Spoke: " + (text.length() > 60 ? text.substring(0, 60) + "…" : text);
    }

    /** Convert an Explorer image to another format with sips, writing alongside the source. */
    public String convertImage(String relativePath, String format) {
        requireMac();
        Path src = fileSystem.resolveExisting(relativePath);
        String fmt = format == null || format.isBlank() ? "jpeg" : format.toLowerCase();
        String sipsFmt = fmt.equals("jpg") ? "jpeg" : fmt;
        String base = src.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        Path dest = src.resolveSibling(stem + "." + fmt);
        run(List.of("sips", "-s", "format", sipsFmt, src.toString(), "--out", dest.toString()), null);
        audit.record("LOCAL", "image_convert", relativePath, "OK", "->" + fmt);
        return "Converted to " + fileSystem.getRoot().relativize(dest);
    }

    public String spotlight(String query) {
        requireMac();
        String out = run(List.of("mdfind", "-name", query), null);
        String[] lines = out.isBlank() ? new String[0] : out.split("\n");
        if (lines.length == 0) {
            return "No Spotlight results for \"" + query + "\".";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 15); i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().trim();
    }

    private void requireMac() {
        if (!mac) {
            throw new ConflictException("Local actions require macOS (host is "
                    + System.getProperty("os.name") + ").");
        }
    }

    /** Run a command with an optional stdin payload; returns stdout. */
    private String run(List<String> command, String stdin) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (stdin != null) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Command timed out: " + command.get(0));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(command.get(0) + " failed: " + out.toString().trim());
            }
            return out.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Local command failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }
}
