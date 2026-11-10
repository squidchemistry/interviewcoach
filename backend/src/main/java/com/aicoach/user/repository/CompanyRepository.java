package com.aicoach.user.repository;

import com.aicoach.user.domain.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    List<Company> findAllByOrderByNameAsc();

    Optional<Company> findBySlug(String slug);
}
