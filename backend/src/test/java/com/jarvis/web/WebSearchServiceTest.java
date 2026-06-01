package com.jarvis.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class WebSearchServiceTest {

    private final WebSearchService web = new WebSearchService(new ObjectMapper());

    @Test
    void parsesAbstractAnswer() {
        String json = "{\"AbstractText\":\"Bitcoin is a cryptocurrency.\",\"AbstractURL\":\"https://en.wikipedia.org/wiki/Bitcoin\",\"RelatedTopics\":[]}";
        String out = web.parse(json, "bitcoin");
        assertThat(out).contains("Bitcoin is a cryptocurrency.");
        assertThat(out).contains("en.wikipedia.org/wiki/Bitcoin");
    }

    @Test
    void fallsBackToRelatedTopics() {
        String json = "{\"AbstractText\":\"\",\"RelatedTopics\":[{\"Text\":\"Apple Silicon is a series of SoCs.\"}]}";
        assertThat(web.parse(json, "apple silicon")).contains("Apple Silicon");
    }

    @Test
    void handlesEmpty() {
        assertThat(web.parse("{\"AbstractText\":\"\",\"RelatedTopics\":[]}", "zzz")).contains("No instant answer");
    }
}
