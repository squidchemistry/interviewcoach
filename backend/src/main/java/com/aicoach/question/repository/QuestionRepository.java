package com.aicoach.question.repository;

import com.aicoach.question.domain.Question;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Optional<Question> findBySlug(String slug);

    List<Question> findBySlugIn(Collection<String> slugs);

    boolean existsBySlug(String slug);

    /** Catalogue search; {@code status} null means any status (admin). Array filters use PostgreSQL operators. */
    @Query(value = """
            SELECT * FROM questions q
            WHERE (CAST(:status AS text) IS NULL OR q.status = :status)
              AND (CAST(:type AS text) IS NULL OR q.type = :type)
              AND (CAST(:difficulty AS text) IS NULL OR q.difficulty = :difficulty)
              AND (CAST(:tag AS text) IS NULL OR :tag = ANY (q.tags))
              AND (CAST(:company AS text) IS NULL OR :company = ANY (q.companies))
              AND (CAST(:query AS text) IS NULL
                   OR to_tsvector('english', q.title || ' ' || q.body_md) @@ plainto_tsquery('english', :query)
                   OR q.title ILIKE '%' || :query || '%')
            ORDER BY q.type, CASE q.difficulty WHEN 'EASY' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, q.title
            """, countQuery = """
            SELECT count(*) FROM questions q
            WHERE (CAST(:status AS text) IS NULL OR q.status = :status)
              AND (CAST(:type AS text) IS NULL OR q.type = :type)
              AND (CAST(:difficulty AS text) IS NULL OR q.difficulty = :difficulty)
              AND (CAST(:tag AS text) IS NULL OR :tag = ANY (q.tags))
              AND (CAST(:company AS text) IS NULL OR :company = ANY (q.companies))
              AND (CAST(:query AS text) IS NULL
                   OR to_tsvector('english', q.title || ' ' || q.body_md) @@ plainto_tsquery('english', :query)
                   OR q.title ILIKE '%' || :query || '%')
            """, nativeQuery = true)
    Page<Question> search(@Param("status") String status, @Param("type") String type,
            @Param("difficulty") String difficulty, @Param("tag") String tag, @Param("company") String company,
            @Param("query") String query, Pageable pageable);

    /** Candidates for interview question selection (published, of a type, optionally a difficulty). */
    @Query("""
            select q from Question q
            where q.status = com.aicoach.question.domain.QuestionEnums.QuestionStatus.PUBLISHED
              and q.type = :type
            """)
    List<Question> findPublishedByType(@Param("type") com.aicoach.question.domain.QuestionEnums.QuestionType type);

    @Query(value = "SELECT DISTINCT unnest(tags) AS tag FROM questions WHERE status = 'PUBLISHED' ORDER BY tag",
            nativeQuery = true)
    List<String> distinctTags();
}
