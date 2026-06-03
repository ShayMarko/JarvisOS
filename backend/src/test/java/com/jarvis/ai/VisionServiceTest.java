package com.jarvis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class VisionServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final VisionService vision = new VisionService(new JarvisAiProperties(), mapper);

    @Test
    void withoutAKeyReturnsAClearMessage() {
        String r = vision.describe(new byte[]{1, 2, 3}, "image/png", "what's this?");
        assertThat(r).contains("API key");
    }

    @Test
    void buildsAnthropicVisionBody() throws Exception {
        String json = mapper.writeValueAsString(vision.anthropicBody("BASE64DATA", "image/png", "what error is shown?"));
        assertThat(json).contains("\"type\":\"image\"").contains("base64").contains("BASE64DATA").contains("what error is shown?");
    }

    @Test
    void buildsOpenAiVisionBody() throws Exception {
        String json = mapper.writeValueAsString(vision.openaiBody("BASE64DATA", "image/jpeg", "describe this UI"));
        assertThat(json).contains("image_url").contains("data:image/jpeg;base64,BASE64DATA").contains("describe this UI");
    }
}
