package com.jarvis.kb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashingEmbeddingModelTest {

    private final EmbeddingModel model = new HashingEmbeddingModel();

    @Test
    void isDeterministicAndNormalised() {
        float[] a = model.embed("the quarterly pricing plan for enterprise");
        float[] b = model.embed("the quarterly pricing plan for enterprise");
        assertThat(a).containsExactly(b);
        double norm = 0;
        for (float v : a) norm += v * v;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void ranksRelatedTextHigherThanUnrelated() {
        float[] q = model.embed("enterprise pricing plan cost");
        double related = EmbeddingModel.cosine(q, model.embed("our enterprise pricing plan raises the cost next quarter"));
        double unrelated = EmbeddingModel.cosine(q, model.embed("a recipe for sourdough bread with a starter"));
        assertThat(related).isGreaterThan(unrelated);
        assertThat(related).isGreaterThan(0.0);
    }
}
