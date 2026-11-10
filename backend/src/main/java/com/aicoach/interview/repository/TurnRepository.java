package com.aicoach.interview.repository;

import com.aicoach.interview.domain.Turn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnRepository extends JpaRepository<Turn, UUID> {

    List<Turn> findBySessionIdOrderBySeq(UUID sessionId);

    Optional<Turn> findBySessionIdAndClientMessageId(UUID sessionId, UUID clientMessageId);

    @Query("select coalesce(max(t.seq), 0) from Turn t where t.sessionId = :sessionId")
    int maxSeq(@Param("sessionId") UUID sessionId);
}
