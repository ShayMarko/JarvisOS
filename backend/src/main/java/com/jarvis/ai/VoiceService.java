package com.jarvis.ai;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Lets Jarvis HEAR and SPEAK — the server-side voice round-trip so it works headless/voice-first. STT via
 * OpenAI Whisper (audio → text), TTS via OpenAI speech (text → mp3). The local model can't do audio, so with
 * no OpenAI key it's wired-but-dormant (transcribe returns a clear message, synthesize returns null). Kept
 * separate from the text agent loop, like VisionService: an agent calls a voice TOOL and gets text back.
 */
@Service
@RequiredArgsConstructor
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);
    private static final int TTS_MAX_CHARS = 4000;   // OpenAI tts input cap is 4096

    private final JarvisAiProperties ai;
    private final JarvisVoiceProperties voice;
    private final ObjectMapper mapper;
    private final RestClient openai = RestClient.create("https://api.openai.com/v1");

    /** Transcribe spoken audio to text. Returns a clear message if no OpenAI key (local model can't hear). */
    public String transcribe(byte[] audio, String filename) {
        if (audio == null || audio.length == 0) {
            return "Error: no audio data.";
        }
        if (blank(ai.getOpenaiApiKey())) {
            return "Voice transcription needs an OpenAI API key (Whisper). Add OPENAI_API_KEY, then try again.";
        }
        try {
            String fn = blank(filename) ? "audio.m4a" : filename;
            MultipartBodyBuilder parts = new MultipartBodyBuilder();
            parts.part("file", new ByteArrayResource(audio) {
                @Override public String getFilename() { return fn; }   // OpenAI infers the format from this
            });
            parts.part("model", voice.getSttModel());
            String json = openai.post().uri("/audio/transcriptions")
                    .header("Authorization", "Bearer " + ai.getOpenaiApiKey())
                    .body(parts.build()).retrieve().body(String.class);
            return mapper.readTree(json == null ? "{}" : json).path("text").asText("").strip();
        } catch (Exception e) {
            log.warn("Transcription failed: {}", e.getMessage());
            return "Couldn't transcribe the audio: " + e.getMessage();
        }
    }

    /** Render text to speech (mp3 bytes), or {@code null} if no key / empty input. */
    public byte[] synthesize(String text, String voiceName) {
        if (blank(text) || blank(ai.getOpenaiApiKey())) {
            return null;
        }
        try {
            String input = text.length() > TTS_MAX_CHARS ? text.substring(0, TTS_MAX_CHARS) : text;
            String v = blank(voiceName) ? voice.getTtsVoice() : voiceName;
            String style = voice.getVoiceStyle();
            // 'instructions' steers delivery on a steerable model (gpt-4o-mini-tts) — this is where the
            // original refined-British persona lives; tts-1/tts-1-hd simply ignore it.
            Map<String, Object> body = blank(style)
                    ? Map.of("model", voice.getTtsModel(), "voice", v, "input", input)
                    : Map.of("model", voice.getTtsModel(), "voice", v, "input", input, "instructions", style);
            return openai.post().uri("/audio/speech")
                    .header("Authorization", "Bearer " + ai.getOpenaiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(body))
                    .retrieve().body(byte[].class);
        } catch (Exception e) {
            log.warn("Speech synthesis failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean ready() {
        return !blank(ai.getOpenaiApiKey());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
