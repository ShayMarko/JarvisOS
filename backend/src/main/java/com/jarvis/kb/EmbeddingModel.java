package com.jarvis.kb;

/**
 * Embedding adapter boundary (mirrors the LanguageModel pattern). The KB depends
 * only on this interface; the default is an offline local embedder, and a neural
 * embedder (OpenAI, a local ONNX model, …) can drop in behind it later.
 */
public interface EmbeddingModel {

    float[] embed(String text);

    int dimension();

    String name();

    /** Cosine similarity of two equal-length, ideally L2-normalised vectors. */
    static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
