package com.aicoach.user.repository;

import com.aicoach.user.domain.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    @EntityGraph(attributePaths = "targetCompanies")
    Optional<Profile> findWithCompaniesByUserId(UUID userId);
}
