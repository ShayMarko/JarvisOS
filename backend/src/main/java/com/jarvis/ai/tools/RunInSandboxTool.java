package com.jarvis.ai.tools;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.sandbox.SandboxResult;
import com.jarvis.sandbox.SandboxService;

import lombok.RequiredArgsConstructor;

/**
 * Runs a shell command to build / install deps for / test / execute a generated project — the
 * capability the developer and tester agents use to actually RUN the app they wrote (not just
 * describe it). Executes inside the project's folder under {@code Projects/} (isolated in a
 * {@code --network none} Docker container when {@code jarvis.limits.sandbox-docker=true}, else a
 * scoped subprocess), with a timeout, captured output, and the same restriction policy as every
 * other sandboxed command (dangerous commands are hard-blocked).
 */
@Component
@RequiredArgsConstructor
public class RunInSandboxTool implements Tool {

    private static final int TIMEOUT_SECONDS = 120;   // builds/tests are slower than a quick command

    private final SandboxService sandbox;
    private final FileSystemService fs;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("run_in_sandbox",
                "Run a shell command to build, install dependencies, test, or execute a project, and see its "
                + "output — use this to verify the app you built actually compiles/runs and to run its tests. "
                + "Set 'project' to the folder under Projects/ (e.g. 'reminders'); omit it to run in a throwaway dir. "
                + "Examples: 'python app.py', 'pytest -q', 'mvn -q -DskipTests package', 'npm install && npm test'.",
                "{\"type\":\"object\",\"properties\":{"
                + "\"command\":{\"type\":\"string\",\"description\":\"the shell command to run\"},"
                + "\"project\":{\"type\":\"string\",\"description\":\"project folder under Projects/, e.g. reminders\"}},"
                + "\"required\":[\"command\"]}");
    }

    @Override
    public boolean mutates() {
        return true;   // executes code / installs deps — a real side effect
    }

    @Override
    public String execute(String args) {
        String command = ToolArgs.firstStr(mapper, args, "command", "cmd", "script");
        if (command == null || command.isBlank()) {
            return "Error: no command provided. Call run_in_sandbox with a \"command\" (and optional \"project\").";
        }
        String project = ToolArgs.firstStr(mapper, args, "project", "app", "dir", "path");
        try {
            SandboxResult r;
            if (project == null || project.isBlank()) {
                r = sandbox.run(command, TIMEOUT_SECONDS);
            } else {
                String rel = project.startsWith("Projects/") ? project : "Projects/" + project;
                Path dir = fs.resolveExisting(rel);   // scoped to the Jarvis root + must exist
                r = sandbox.runIn(dir, command, TIMEOUT_SECONDS);
            }
            return format(command, r);
        } catch (RuntimeException e) {
            return "Error running command: " + e.getMessage();
        }
    }

    private static String format(String command, SandboxResult r) {
        String out = r.output() == null ? "" : r.output().strip();
        if (out.length() > 4000) {
            out = out.substring(0, 4000) + "\n…(truncated)";
        }
        String status = r.timedOut()
                ? "TIMED OUT after " + (r.durationMs() / 1000) + "s"
                : "exit " + r.exitCode() + " in " + r.durationMs() + "ms";
        return "$ " + command + "\n(" + status + ")\n" + (out.isBlank() ? "(no output)" : out);
    }
}
