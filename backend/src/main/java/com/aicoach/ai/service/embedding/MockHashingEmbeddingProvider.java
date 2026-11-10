package com.aicoach.ai.service.embedding;

import com.aicoach.ai.AiTypes.EmbeddingProvider;
import com.aicoach.common.security.Hashing;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, offline embeddings via signed feature hashing of words and word bigrams, L2-normalised.
 * Similarity is lexical rather than semantic: good enough for "similar questions" in demos and tests.
 */
public class MockHashingEmbeddingProvider implements EmbeddingProvider {

    private static final Set<String> STOPWORDS = Set.of("the", "a", "an", "and", "or", "of", "to", "in", "on", "for",
            "is", "are", "be", "with", "that", "this", "it", "as", "by", "you", "your", "at", "from", "each", "given",
            "return", "returns", "if", "can", "all", "any");

    @Override
    public String model() {
        return "mock-hashing-1024";
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        String[] words = (text == null ? "" : text.toLowerCase(Locale.ROOT)).split("[^a-z0-9+#]+");
        String previous = null;
        for (String word : words) {
            if (word.length() < 2 || STOPWORDS.contains(word)) {
                continue;
            }
            add(vector, word, 1.0f);
            if (previous != null) {
                add(vector, previous + " " + word, 0.5f);
            }
            previous = word;
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm > 0) {
            float inv = (float) (1 / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= inv;
            }
        }
        return vector;
    }

    private static void add(float[] vector, String feature, float weight) {
        byte[] hash = Hashing.sha256(feature);
        int index = ((hash[0] & 0xff) << 8 | (hash[1] & 0xff)) % DIMENSIONS;
        vector[index] += (hash[2] & 1) == 0 ? weight : -weight;
    }
}
