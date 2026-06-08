package com.jarvis.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code jarvis.voice} — the server-side voice round-trip (speech-to-text + text-to-speech), used so
 * Jarvis works headless/voice-first: an audio clip (phone/Discord voice note, a recording) is transcribed,
 * run through the brain, and optionally answered back as spoken audio. Uses OpenAI (Whisper + TTS); the
 * local model can't do audio, so it's dormant-until-keyed (graceful message otherwise).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.voice")
public class JarvisVoiceProperties {

    /** Speech-to-text model (OpenAI). */
    private String sttModel = "whisper-1";
    /** Text-to-speech model. gpt-4o-mini-tts is steerable (honours {@code voiceStyle}); tts-1/tts-1-hd ignore it. */
    private String ttsModel = "gpt-4o-mini-tts";
    /** Base TTS voice timbre (alloy | echo | fable | onyx | nova | shimmer). 'fable' = British-leaning. */
    private String ttsVoice = "fable";
    /**
     * The ORIGINAL voice persona — a refined British AI-assistant vibe (same feel as a certain on-screen
     * assistant, but NOT a clone of any real actor or copyrighted character; that last clause keeps it legal).
     * Sent as the OpenAI `instructions` field on a steerable model, so it shapes the delivery, not the words.
     */
    private String voiceStyle = "Speak like a refined, intelligent personal AI assistant. Use a calm British "
            + "tone, precise pronunciation, composed pacing, subtle warmth and a touch of dry wit. Sound "
            + "futuristic and premium, but do NOT imitate any real actor or copyrighted character.";
    /** Where generated speech audio is saved (under the Jarvis root). */
    private String outputDir = "Generated/voice";
}
