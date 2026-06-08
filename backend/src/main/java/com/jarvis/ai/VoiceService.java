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

    /** Render text to speech, or {@code null} on empty input / failure. Uses the FREE local macOS voice by
     *  default; the neural OpenAI voice only when ttsProvider=openai AND a key is set. */
    public byte[] synthesize(String text, String voiceName) {
        if (blank(text)) {
            return null;
        }
        return useOpenAi() ? openAiTts(text, voiceName) : localTts(text, voiceName);
    }

    /** File extension synthesize() produces with the current settings (mp3 = OpenAI, aiff = local say). */
    public String outputExtension() {
        return useOpenAi() ? "mp3" : "aiff";
    }

    public boolean ready() {
        // Local say is the default and ~always available on macOS; OpenAI path needs a key.
        return useOpenAi() || !"openai".equalsIgnoreCase(voice.getTtsProvider());
    }

    private boolean useOpenAi() {
        return "openai".equalsIgnoreCase(voice.getTtsProvider()) && !blank(ai.getOpenaiApiKey());
    }

    /** The voices you can choose from for the active provider — the menu for "pick a voice". */
    public java.util.List<String> availableVoices() {
        if (useOpenAi()) {
            return java.util.List.of("alloy", "echo", "fable", "onyx", "nova", "shimmer");
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            Process p = new ProcessBuilder("say", "-v", "?").redirectErrorStream(true).start();
            try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                java.util.regex.Pattern line = java.util.regex.Pattern.compile("^(.*?)\\s{2,}([a-z]{2}_[A-Z]{2})\\b");
                String s;
                while ((s = r.readLine()) != null) {
                    java.util.regex.Matcher m = line.matcher(s);
                    if (m.find()) {
                        out.add(m.group(1).trim() + "  (" + m.group(2) + ")");
                    }
                }
            }
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Could not list local voices: {}", e.getMessage());
        }
        return out;
    }

    /** Neural OpenAI TTS (mp3). The voiceStyle persona steers delivery on gpt-4o-mini-tts. */
    private byte[] openAiTts(String text, String voiceName) {
        try {
            String input = text.length() > TTS_MAX_CHARS ? text.substring(0, TTS_MAX_CHARS) : text;
            String v = blank(voiceName) ? voice.getTtsVoice() : voiceName;
            String style = voice.getVoiceStyle();
            Map<String, Object> body = blank(style)
                    ? Map.of("model", voice.getTtsModel(), "voice", v, "input", input)
                    : Map.of("model", voice.getTtsModel(), "voice", v, "input", input, "instructions", style);
            return openai.post().uri("/audio/speech")
                    .header("Authorization", "Bearer " + ai.getOpenaiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(body))
                    .retrieve().body(byte[].class);
        } catch (Exception e) {
            log.warn("OpenAI TTS failed: {}", e.getMessage());
            return null;
        }
    }

    /** FREE, offline TTS via macOS `say` → aiff bytes (no key, no limits). Null if `say` is unavailable. */
    private byte[] localTts(String text, String voiceName) {
        java.nio.file.Path tmp = null;
        try {
            tmp = java.nio.file.Files.createTempFile("jarvis-tts", ".aiff");
            String v = blank(voiceName) ? voice.getLocalVoice() : voiceName;
            Process p = new ProcessBuilder("say", "-v", v, "-r", String.valueOf(voice.getLocalRate()),
                    "-o", tmp.toString(), text).redirectErrorStream(true).start();
            if (!p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 ? java.nio.file.Files.readAllBytes(tmp) : null;
        } catch (Exception e) {
            log.warn("Local TTS (say) failed: {}", e.getMessage());
            return null;
        } finally {
            if (tmp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                    // best-effort temp cleanup
                }
            }
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
