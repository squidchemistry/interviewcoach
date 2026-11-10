package com.aicoach.user.repository;

import com.aicoach.user.domain.Role;
import com.aicoach.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** {@code email} must already be normalised (trimmed, lower-case). */
    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where u.deletedAt is null
              and (:role is null or u.role = :role)
              and (:pattern is null
                   or lower(u.email) like :pattern escape '\\'
                   or lower(u.name) like :pattern escape '\\')
            """)
    Page<User> searchActive(@Param("pattern") String pattern, @Param("role") Role role, Pageable pageable);
}
