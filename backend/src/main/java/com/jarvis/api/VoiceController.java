package com.jarvis.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.ai.JarvisVoiceProperties;
import com.jarvis.ai.VoiceService;
import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;
import com.jarvis.common.Ids;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/**
 * The server-side voice round-trip (headless/voice-first): point it at an audio file in the Explorer
 * (a phone/Discord voice note, a recording) and Jarvis transcribes it, optionally runs it through the
 * brain, and can speak the answer back as an mp3. Reads/writes go through the Explorer, so it works with
 * no screen — drop an audio file, get a transcript (and a spoken reply file) back.
 */
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final FileSystemService fs;
    private final VoiceService voice;
    private final Orchestrator orchestrator;
    private final JarvisVoiceProperties props;

    public record TranscribeRequest(String path) {}
    public record AskRequest(String path, String sessionId, boolean speak) {}
    public record SpeakRequest(String text, String voice) {}

    /** Audio file → text. */
    @PostMapping("/transcribe")
    public Map<String, Object> transcribe(@RequestBody TranscribeRequest req) {
        if (req == null || req.path() == null || req.path().isBlank()) {
            return Map.of("result", "Provide the audio 'path' (under the Explorer).");
        }
        String text = transcribePath(req.path());
        return Map.of("transcript", text);
    }

    /** Audio file → transcript → brain answer (+ optional spoken-reply mp3). The headless voice loop. */
    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody AskRequest req) {
        if (req == null || req.path() == null || req.path().isBlank()) {
            return Map.of("result", "Provide the audio 'path' (under the Explorer).");
        }
        String transcript = transcribePath(req.path());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transcript", transcript);
        if (transcript.isBlank() || transcript.startsWith("Error") || transcript.startsWith("Couldn't")
                || transcript.startsWith("Voice transcription needs")) {
            out.put("answer", transcript);
            return out;
        }
        String session = req.sessionId() == null || req.sessionId().isBlank() ? "voice" : req.sessionId();
        ChatResponse resp = orchestrator.handle(transcript, session);
        String answer = resp == null || resp.answer() == null ? "" : resp.answer();
        out.put("answer", answer);
        out.put("agent", resp == null ? null : resp.agent());
        if (req.speak()) {
            out.put("audioPath", speakToFile(answer, null));
        }
        return out;
    }

    /** Text → spoken mp3 saved under the Explorer. */
    @PostMapping("/speak")
    public Map<String, Object> speak(@RequestBody SpeakRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            return Map.of("result", "Provide 'text' to speak.");
        }
        String path = speakToFile(req.text(), req.voice());
        return path == null
                ? Map.of("result", "Speech needs an OpenAI API key (TTS). Add OPENAI_API_KEY, then try again.")
                : Map.of("audioPath", path);
    }

    private String transcribePath(String path) {
        try {
            Path p = fs.resolveExisting(path);
            return voice.transcribe(Files.readAllBytes(p), p.getFileName().toString());
        } catch (Exception e) {
            return "Error reading audio: " + e.getMessage();
        }
    }

    /** Synthesize speech and save it; returns the Explorer path, or null if no key / failure. */
    private String speakToFile(String text, String voiceName) {
        byte[] mp3 = voice.synthesize(text, voiceName);
        if (mp3 == null) {
            return null;
        }
        String rel = props.getOutputDir() + "/" + Ids.generate("speech", 8) + ".mp3";
        fs.writeBytes(rel, mp3);
        return rel;
    }
}
