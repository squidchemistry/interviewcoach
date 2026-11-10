package com.aicoach.question.repository;

import com.aicoach.question.domain.QuestionPack;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionPackRepository extends JpaRepository<QuestionPack, UUID> {

    Optional<QuestionPack> findBySlug(String slug);

    List<QuestionPack> findAllByOrderByNameAsc();
}
