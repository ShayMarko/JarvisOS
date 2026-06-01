package com.jarvis.kb;

import java.util.Set;

/**
 * Offline, deterministic embedding via the hashing trick + sublinear TF, then
 * L2-normalisation (spec §10.2 Vector Search). This is real information
 * retrieval — lexical/semantic ranking by cosine, far better than substring
 * matching — and needs no API key or model download. Swap in a neural
 * {@link EmbeddingModel} later for true semantic embeddings.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 512;
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "is", "it", "for", "on", "with",
            "that", "this", "as", "are", "was", "be", "by", "at", "from", "my", "i", "you");

    @Override
    public int dimension() {
        return DIM;
    }

    @Override
    public String name() {
        return "hashing-tf-" + DIM;
    }

    @Override
    public float[] embed(String text) {
        float[] vec = new float[DIM];
        if (text == null || text.isBlank()) {
            return vec;
        }
        for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
            if (token.length() < 2 || STOPWORDS.contains(token)) {
                continue;
            }
            int bucket = Math.floorMod(token.hashCode(), DIM);
            vec[bucket] += 1f;
        }
        // sublinear term frequency: dampen repeated terms
        for (int i = 0; i < DIM; i++) {
            if (vec[i] > 0) {
                vec[i] = (float) (1 + Math.log(vec[i]));
            }
        }
        // L2-normalise so cosine is a plain dot product
        double norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIM; i++) {
                vec[i] /= (float) norm;
            }
        }
        return vec;
    }
}
