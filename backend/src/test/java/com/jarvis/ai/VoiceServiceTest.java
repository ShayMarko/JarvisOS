package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class VoiceServiceTest {

    private VoiceService service() {
        return new VoiceService(new JarvisAiProperties(), new JarvisVoiceProperties(), new ObjectMapper());
    }

    @Test
    void emptyAudioIsRejectedBeforeAnyCall() {
        assertThat(service().transcribe(new byte[0], "a.mp3")).contains("no audio");
    }

    @Test
    void transcribeWithoutKeyIsGracefulNotAnError() {
        String out = service().transcribe(new byte[]{1, 2, 3}, "a.mp3");
        assertThat(out).contains("OpenAI API key");   // wired-but-dormant, clear message
    }

    @Test
    void synthesizeWithoutKeyReturnsNull() {
        assertThat(service().synthesize("hello", null)).isNull();
        assertThat(service().synthesize("", null)).isNull();
    }

    @Test
    void notReadyWithoutKey() {
        assertThat(service().ready()).isFalse();
    }
}
