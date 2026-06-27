package com.jarvis.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.voice.daemon} — the always-on, headless, voice-first loop that lets the Mac mini
 * "fly" with no monitor: listen → wake-word → transcribe (local whisper.cpp) → Brain → speak aloud.
 *
 * <p>Dormant by default ({@code enabled=false}) so a normal install is unaffected. The record/transcribe
 * commands are templates (with {@code {wav}} / {@code {model}} placeholders) so you can match your exact
 * whisper.cpp + ffmpeg/sox build without code changes. Requires Microphone permission on the mini.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.voice.daemon")
public class JarvisVoiceDaemonProperties {

    /** Master switch. Off → the daemon never starts (no mic capture, no CPU use). */
    private boolean enabled = false;

    /** Spoken trigger; an utterance only acts if it contains this (case-insensitive). */
    private String wakeWord = "jarvis";

    /** Seconds of audio captured per listen window. */
    private int windowSeconds = 5;

    /** Pause between windows (ms) when nothing was heard, to keep CPU sane. */
    private long pauseMs = 400;

    /** Path to the whisper.cpp model (e.g. ggml-base.en.bin). */
    private String whisperModel = "models/ggml-base.en.bin";

    /**
     * Command that records one window to a 16kHz mono WAV at {wav}. Default uses ffmpeg + macOS avfoundation
     * default mic (":0"). Override the device index if needed (ffmpeg -f avfoundation -list_devices true -i "").
     */
    private String recordCommand =
            "ffmpeg -nostdin -loglevel error -f avfoundation -i :0 -t {sec} -ar 16000 -ac 1 -y {wav}";

    /** Command that transcribes {wav} with the model {model} and prints plain text to stdout (whisper.cpp). */
    private String transcribeCommand =
            "whisper-cli -m {model} -f {wav} -nt -np -otxt -of {wav}";
}
