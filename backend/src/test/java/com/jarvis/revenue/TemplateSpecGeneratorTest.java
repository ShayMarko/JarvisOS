package com.jarvis.revenue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;
import com.jarvis.revenue.TemplateSpecGenerator.Result;

class TemplateSpecGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reserveSoldierUsesCachedSeedWithZeroAiCalls() {
        LanguageModel model = mock(LanguageModel.class);
        TemplateSpecGenerator gen = new TemplateSpecGenerator(model, mapper);

        Result r = gen.generate("a Notion template for reserve soldiers", "he", "Israeli reserve soldiers", "medium");

        assertThat(r.aiCallsUsed()).isZero();
        assertThat(r.spec().databases()).isNotEmpty();
        assertThat(r.spec().title()).contains("מילואים");      // Hebrew flagship
        verify(model, never()).generate(anyList(), anyList()); // no AI call for the seed
    }

    @Test
    void unknownIdeaMakesOneAiCallAndParsesTheSpec() {
        LanguageModel model = mock(LanguageModel.class);
        when(model.generate(anyList(), anyList())).thenReturn(ModelResponse.text(
                "{\"title\":\"Budget Tracker\",\"language\":\"en\",\"databases\":[],\"pages\":[],\"salesCopy\":\"x\"}", 5, 5));
        TemplateSpecGenerator gen = new TemplateSpecGenerator(model, mapper);

        Result r = gen.generate("a personal budget tracker", "en", "freelancers", "simple");

        assertThat(r.aiCallsUsed()).isEqualTo(1);
        assertThat(r.spec().title()).isEqualTo("Budget Tracker");
    }
}
