package com.jarvis.ai.tools;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.ai.VoiceService;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/**
 * Transcribes a spoken-audio file in the Explorer (voice memo, meeting/call recording, a voice note) to
 * text via Whisper — so the Meeting Assistant and others can work from audio. Needs an OpenAI key (returns
 * a clear message otherwise). The model then reasons over the returned transcript like any tool result.
 */
@Component
@RequiredArgsConstructor
public class TranscribeAudioTool implements Tool {

    private final VoiceService voice;
    private final FileSystemService fs;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("transcribe_audio",
                "Transcribe a spoken-audio file (voice memo, meeting/call recording) under the Explorer to text.",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\","
                + "\"description\":\"Path to the audio file (mp3/m4a/wav/…) under the Jarvis Explorer\"}},"
                + "\"required\":[\"path\"]}");
    }

    @Override
    public String execute(String args) {
        try {
            String path = ToolArgs.str(mapper, args, "path");
            if (path.isBlank()) {
                return "Provide the audio 'path' (under the Explorer).";
            }
            Path p = fs.resolveExisting(path);
            return voice.transcribe(Files.readAllBytes(p), p.getFileName().toString());
        } catch (Exception e) {
            return "Error reading audio: " + e.getMessage();
        }
    }
}
