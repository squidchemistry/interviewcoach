package com.aicoach.question.repository;

import com.aicoach.question.domain.Rubric;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RubricRepository extends JpaRepository<Rubric, UUID> {

    Optional<Rubric> findByKeyAndActiveTrue(String key);

    List<Rubric> findAllByOrderByKeyAscVersionDesc();

    @Query("select coalesce(max(r.version), 0) from Rubric r where r.key = :key")
    int maxVersion(String key);
}
