package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class VoiceServiceTest {

    /** Default config: FREE local macOS `say` TTS, no keys. */
    private VoiceService service() {
        return new VoiceService(new JarvisAiProperties(), new JarvisVoiceProperties(), new ObjectMapper());
    }

    /** OpenAI TTS path explicitly selected + keyed. */
    private VoiceService openAiKeyed() {
        JarvisAiProperties ai = new JarvisAiProperties();
        ai.setOpenaiApiKey("sk-test");
        JarvisVoiceProperties v = new JarvisVoiceProperties();
        v.setTtsProvider("openai");
        return new VoiceService(ai, v, new ObjectMapper());
    }

    @Test
    void emptyAudioIsRejectedBeforeAnyCall() {
        assertThat(service().transcribe(new byte[0], "a.mp3")).contains("no audio");
    }

    @Test
    void transcribeWithoutKeyIsGracefulNotAnError() {
        // STT (Whisper) has no free local fallback — needs an OpenAI key. Clear message, not a crash.
        assertThat(service().transcribe(new byte[]{1, 2, 3}, "a.mp3")).contains("OpenAI API key");
    }

    @Test
    void emptyTextSynthesizesNothing() {
        assertThat(service().synthesize("", null)).isNull();
        assertThat(service().synthesize(null, null)).isNull();
    }

    @Test
    void localIsTheDefaultProvider() {
        // FREE local say is the default → ready without any key, and produces .aiff.
        assertThat(service().ready()).isTrue();
        assertThat(service().outputExtension()).isEqualTo("aiff");
    }

    @Test
    void openAiPathProducesMp3WhenSelectedAndKeyed() {
        assertThat(openAiKeyed().outputExtension()).isEqualTo("mp3");
        assertThat(openAiKeyed().ready()).isTrue();
    }
}
