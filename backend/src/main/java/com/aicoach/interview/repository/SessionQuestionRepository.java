package com.aicoach.interview.repository;

import com.aicoach.interview.domain.SessionQuestion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, UUID> {

    List<SessionQuestion> findBySessionIdOrderByPosition(UUID sessionId);

    Optional<SessionQuestion> findBySessionIdAndPosition(UUID sessionId, int position);

    /** Bank questions this user was asked recently (so selection avoids repeats). */
    @Query(value = """
            SELECT DISTINCT sq.question_id FROM session_questions sq
            JOIN interview_sessions s ON s.id = sq.session_id
            WHERE s.user_id = :userId AND sq.question_id IS NOT NULL AND sq.started_at >= :since
            """, nativeQuery = true)
    List<UUID> recentlyAskedQuestionIds(@Param("userId") UUID userId, @Param("since") Instant since);
}
