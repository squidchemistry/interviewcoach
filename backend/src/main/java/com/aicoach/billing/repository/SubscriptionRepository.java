package com.aicoach.billing.repository;

import com.aicoach.billing.domain.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId);

    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
