package com.aicoach.question.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The only place that touches the {@code vector} column: parameterised native SQL for writing embeddings and cosine
 * nearest-neighbour search (HNSW index, operator {@code <=>}).
 */
@Repository
public class QuestionEmbeddingRepository {

    private final JdbcTemplate jdbc;

    public QuestionEmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PendingEmbedding(UUID id, String text) {
    }

    public record Neighbour(UUID id, double similarity) {
    }

    /** Questions with no embedding, or one produced by a different model (after switching providers). */
    public List<PendingEmbedding> findPending(String model, int limit) {
        return jdbc.query("""
                SELECT id, title || E'\\n' || array_to_string(tags, ', ') || E'\\n' || body_md AS text
                FROM questions
                WHERE embedding IS NULL OR embedding_model IS DISTINCT FROM ?
                ORDER BY created_at
                LIMIT ?
                """, (rs, i) -> new PendingEmbedding(rs.getObject("id", UUID.class), rs.getString("text")), model, limit);
    }

    public void save(UUID id, float[] vector, String model) {
        jdbc.update("UPDATE questions SET embedding = CAST(? AS vector), embedding_model = ?, embedded_at = now() WHERE id = ?",
                literal(vector), model, id);
    }

    public void clear(UUID id) {
        jdbc.update("UPDATE questions SET embedding = NULL, embedding_model = NULL, embedded_at = NULL WHERE id = ?", id);
    }

    /** Top-k published questions most similar to {@code id} (cosine), excluding itself. */
    public List<Neighbour> similarTo(UUID id, int k) {
        return jdbc.query("""
                SELECT q.id, 1 - (q.embedding <=> src.embedding) AS similarity
                FROM questions q, (SELECT embedding FROM questions WHERE id = ?) src
                WHERE q.id <> ? AND q.status = 'PUBLISHED' AND q.embedding IS NOT NULL AND src.embedding IS NOT NULL
                ORDER BY q.embedding <=> src.embedding
                LIMIT ?
                """, (rs, i) -> new Neighbour(rs.getObject("id", UUID.class), rs.getDouble("similarity")), id, id, k);
    }

    static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
