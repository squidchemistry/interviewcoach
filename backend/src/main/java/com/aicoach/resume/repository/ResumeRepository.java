package com.aicoach.resume.repository;

import com.aicoach.resume.domain.Resume;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);

    Optional<Resume> findByUserIdAndActiveTrue(UUID userId);

    long countByUserId(UUID userId);

    @Modifying
    @Query("UPDATE Resume r SET r.active = false WHERE r.userId = :userId AND r.active = true")
    int deactivateAll(@Param("userId") UUID userId);
}
