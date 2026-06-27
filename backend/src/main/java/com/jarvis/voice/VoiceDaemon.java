package com.jarvis.voice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jarvis.brain.Orchestrator;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * Headless, always-on voice loop so the monitor-less wall-panel Mac mini can "fly":
 * record a window → local whisper.cpp transcribe → if it contains the wake word, send to the Brain →
 * speak the answer aloud (macOS {@code say}). No browser, no screen.
 *
 * <p>Dormant unless {@code jarvis.voice.daemon.enabled=true}. All external commands (record/transcribe)
 * are config templates so it adapts to your whisper.cpp/ffmpeg build. Needs Microphone permission on the mini.
 */
@Component
@RequiredArgsConstructor
public class VoiceDaemon {

    private static final Logger log = LoggerFactory.getLogger(VoiceDaemon.class);
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    private final JarvisVoiceDaemonProperties props;
    private final Orchestrator orchestrator;

    private volatile boolean running;
    private Thread thread;

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        if (!props.isEnabled()) {
            log.info("Voice daemon off (jarvis.voice.daemon.enabled=false) — dormant.");
            return;
        }
        if (!MAC) {
            log.warn("Voice daemon needs macOS for `say`/avfoundation — not starting on {}.", System.getProperty("os.name"));
            return;
        }
        running = true;
        thread = new Thread(this::loop, "voice-daemon");
        thread.setDaemon(true);
        thread.start();
        log.info("🎙️ Voice daemon listening — wake word \"{}\", {}s windows.", props.getWakeWord(), props.getWindowSeconds());
    }

    @PreDestroy
    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        Path wav;
        try {
            wav = Files.createTempFile("jarvis-voice", ".wav");
        } catch (Exception e) {
            log.error("Voice daemon couldn't create a temp file — stopping: {}", e.getMessage());
            return;
        }
        boolean warnedRecord = false;
        boolean warnedStt = false;
        while (running) {
            try {
                if (!record(wav)) {
                    if (!warnedRecord) { log.warn("Voice daemon: record command failed (is ffmpeg installed + Mic permission granted?). Will keep retrying quietly."); warnedRecord = true; }
                    sleep(2000);
                    continue;
                }
                String heard = transcribe(wav);
                if (heard.isBlank()) {
                    if (!warnedStt && !Files.exists(Path.of(props.getWhisperModel()))) { log.warn("Voice daemon: whisper model not found at {} (install whisper.cpp + download a model).", props.getWhisperModel()); warnedStt = true; }
                    sleep(props.getPauseMs());
                    continue;
                }
                handleIfAddressed(heard);
            } catch (Exception e) {
                log.debug("Voice daemon loop error: {}", e.getMessage());
                sleep(1000);
            }
        }
    }

    /** If the utterance names the wake word, strip it and run the rest through the Brain, then speak the reply. */
    private void handleIfAddressed(String heard) {
        String lower = heard.toLowerCase();
        int idx = lower.indexOf(props.getWakeWord().toLowerCase());
        if (idx < 0) {
            return;   // not addressed to Jarvis — ignore
        }
        String command = heard.substring(idx + props.getWakeWord().length()).replaceFirst("^[\\s,:.!-]+", "").trim();
        if (command.isBlank()) {
            speak("Yes?");
            return;
        }
        log.info("🎙️ Heard: \"{}\"", command);
        try {
            String answer = orchestrator.handle(command, "voice").answer();
            speak(answer == null || answer.isBlank() ? "Done." : answer);
        } catch (Exception e) {
            speak("Sorry, I hit an error: " + e.getMessage());
        }
    }

    private boolean record(Path wav) {
        String cmd = props.getRecordCommand()
                .replace("{wav}", wav.toString())
                .replace("{sec}", String.valueOf(props.getWindowSeconds()));
        return run(cmd, props.getWindowSeconds() + 8) != null && Files.exists(wav) && wav.toFile().length() > 1024;
    }

    private String transcribe(Path wav) {
        String cmd = props.getTranscribeCommand()
                .replace("{wav}", wav.toString())
                .replace("{model}", props.getWhisperModel());
        String stdout = run(cmd, 60);
        // whisper.cpp with -otxt -of {wav} writes {wav}.txt; prefer that, else fall back to stdout.
        try {
            Path txt = Path.of(wav + ".txt");
            if (Files.exists(txt)) {
                String t = Files.readString(txt).trim();
                Files.deleteIfExists(txt);
                return t;
            }
        } catch (Exception ignored) { /* fall through to stdout */ }
        return stdout == null ? "" : stdout.trim();
    }

    /** Speak aloud through the mini's speakers (free, local macOS `say`). */
    private void speak(String text) {
        String t = text.length() > 1000 ? text.substring(0, 1000) : text;
        run("say " + shellSafe(t), 60);
    }

    private static String shellSafe(String s) {
        // say takes the text as args; pass as a single quoted token to avoid the shell mangling it.
        return "'" + s.replace("'", "’").replace("\n", " ") + "'";
    }

    /** Run a whitespace-split command with a timeout; returns stdout, or null on failure. */
    private String run(String command, long timeoutSeconds) {
        try {
            List<String> parts = Arrays.stream(command.trim().split("\\s+")).toList();
            // `say '...'` needs the quoted text kept whole → run via the shell for the speak path.
            ProcessBuilder pb = command.startsWith("say ")
                    ? new ProcessBuilder("/bin/sh", "-c", command)
                    : new ProcessBuilder(parts);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 ? out.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
