package com.aicoach.interview.repository;

import com.aicoach.interview.domain.Evaluation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    Optional<Evaluation> findBySessionId(UUID sessionId);

    List<Evaluation> findBySessionIdIn(java.util.Collection<UUID> sessionIds);

    /** Evaluations that should be (re)run: pending, or "running" for too long (the worker died). */
    @Query("""
            select e.sessionId from Evaluation e
            where (e.status = 'PENDING' or e.status = 'RUNNING') and e.updatedAt < :before and e.attempts < 3
            """)
    List<UUID> findStalled(@Param("before") Instant before);
}
