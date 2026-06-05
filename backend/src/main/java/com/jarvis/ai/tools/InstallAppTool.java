package com.jarvis.ai.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;

import lombok.RequiredArgsConstructor;

/**
 * Installs software the agent chooses — "install spotify" / "install vlc" / "install prettier".
 * The model decides the package + manager (Homebrew cask for GUI apps, Homebrew formula for CLI
 * tools, npm for global node packages); this tool just runs it safely.
 *
 * <p>Safety: the package name is validated against a strict allow-list of characters and the command
 * is run as an argument array (never a shell string), so there is no shell-injection surface. macOS
 * only (Homebrew); fails soft elsewhere. It's a real side effect, so {@link #mutates()} is true.
 */
@Component
@RequiredArgsConstructor
public class InstallAppTool implements Tool {

    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("install_app",
                "Install an app or package on the Mac. Pick the manager: 'cask' for GUI apps (Spotify, VLC), "
                + "'brew' for CLI tools (ffmpeg, jq), or 'npm' for global node packages (prettier, typescript).",
                "{\"type\":\"object\",\"properties\":{"
                + "\"package\":{\"type\":\"string\",\"description\":\"the package/app name, e.g. spotify, vlc, prettier\"},"
                + "\"manager\":{\"type\":\"string\",\"enum\":[\"cask\",\"brew\",\"npm\"],\"description\":\"cask=GUI app, brew=CLI tool, npm=node package\"}},"
                + "\"required\":[\"package\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    /** Installing software on the real machine is system-changing — gate it behind your approval. */
    @Override
    public com.jarvis.security.RiskLevel riskLevel() {
        return com.jarvis.security.RiskLevel.HIGH;
    }

    @Override
    public String execute(String args) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return "Error: install_app needs macOS (Homebrew/npm). Host is " + System.getProperty("os.name") + ".";
        }
        String pkg = ToolArgs.firstStr(mapper, args, "package", "app", "name");
        String manager = ToolArgs.firstStr(mapper, args, "manager", "via", "with");
        if (pkg == null || pkg.isBlank()) {
            return "Error: no package name provided. Call install_app with {\"package\":\"<name>\",\"manager\":\"cask|brew|npm\"}.";
        }
        // Injection-safe: reject anything that isn't a plausible package identifier.
        if (!pkg.matches("[A-Za-z0-9@._/+-]{1,100}")) {
            return "Error: \"" + pkg + "\" is not a valid package name.";
        }
        List<String> command = buildCommand(pkg, manager == null ? "" : manager.toLowerCase());
        return run(command);
    }

    /** Map (package, manager) to a concrete, fully-resolved install command. */
    private List<String> buildCommand(String pkg, String manager) {
        List<String> cmd = new ArrayList<>();
        if (manager.equals("npm")) {
            cmd.add(resolve("npm", "/opt/homebrew/bin/npm", "/usr/local/bin/npm"));
            cmd.add("install");
            cmd.add("-g");
            cmd.add(pkg);
            return cmd;
        }
        String brew = resolve("brew", "/opt/homebrew/bin/brew", "/usr/local/bin/brew");
        cmd.add(brew);
        cmd.add("install");
        if (manager.equals("cask")) {
            cmd.add("--cask");
        }
        cmd.add(pkg);
        return cmd;
    }

    /** First existing absolute path, else the bare name (relies on PATH). */
    private static String resolve(String bare, String... candidates) {
        for (String c : candidates) {
            if (Files.isExecutable(Path.of(c))) {
                return c;
            }
        }
        return bare;
    }

    private String run(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);   // installs can be slow
            if (!finished) {
                process.destroyForcibly();
                return "Error: install timed out after 180s for " + command.get(command.size() - 1) + ".";
            }
            String tail = tail(out.toString(), 600);
            return process.exitValue() == 0
                    ? "Installed " + command.get(command.size() - 1) + ".\n" + tail
                    : "Install failed (exit " + process.exitValue() + "):\n" + tail;
        } catch (java.io.IOException e) {
            return "Error: couldn't start " + command.get(0) + " — is it installed and on PATH? (" + e.getMessage() + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: install interrupted.";
        }
    }

    private static String tail(String s, int max) {
        String t = s.strip();
        return t.length() > max ? t.substring(t.length() - max) : t;
    }
}
