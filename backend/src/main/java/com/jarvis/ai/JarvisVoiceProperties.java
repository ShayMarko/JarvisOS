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

    /**
     * TTS provider: "local" = macOS `say` (FREE, offline, no key — the DEFAULT) or "openai" = neural
     * gpt-4o-mini-tts (needs a key, more lifelike + honours the voiceStyle persona). If set to "openai"
     * but no key is present, it falls back to the free local voice rather than going silent.
     */
    private String ttsProvider = "local";
    /**
     * macOS `say` voice for the FREE local provider. "Daniel" is the built-in British male (works out of the
     * box, a bit robotic). For near-neural quality download a Premium/Enhanced voice (System Settings →
     * Accessibility → Spoken Content → Manage Voices → e.g. "Daniel (Premium)") and put its exact name here.
     */
    private String localVoice = "Jamie (Premium)";
    /** Speaking rate (words/min) for the local voice — lower = more composed/Jarvis-like. */
    private int localRate = 170;

    /** Speech-to-text model (OpenAI — there is no built-in free local STT). */
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
