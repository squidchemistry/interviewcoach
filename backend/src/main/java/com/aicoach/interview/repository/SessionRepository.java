package com.aicoach.interview.repository;

import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.SessionStatus;
import com.aicoach.interview.domain.InterviewSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<InterviewSession, UUID> {

    @Query("""
            select s from InterviewSession s
            where s.userId = :userId
              and (:status is null or s.status = :status)
              and (:mode is null or s.mode = :mode)
            order by s.createdAt desc
            """)
    Page<InterviewSession> findForUser(@Param("userId") UUID userId, @Param("status") SessionStatus status,
            @Param("mode") InterviewMode mode, Pageable pageable);

    /** In-progress sessions idle since {@code before} (inactivity sweeper). */
    @Query("select s.id from InterviewSession s where s.status = 'IN_PROGRESS' and s.lastActivityAt < :before")
    List<UUID> findIdle(@Param("before") Instant before);

    /** In-progress sessions stuck "processing" (the async step died with its process). */
    @Query("select s.id from InterviewSession s where s.status = 'IN_PROGRESS' and s.processing = true and s.lastActivityAt < :before")
    List<UUID> findStuckProcessing(@Param("before") Instant before);
}
