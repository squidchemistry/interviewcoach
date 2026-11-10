package com.aicoach.billing.service;

import com.aicoach.audit.service.AuditService;
import com.aicoach.billing.domain.Subscription;
import com.aicoach.billing.dto.BillingDtos.CheckoutResponse;
import com.aicoach.billing.dto.BillingDtos.PlanResponse;
import com.aicoach.billing.dto.BillingDtos.SubscriptionResponse;
import com.aicoach.billing.dto.BillingDtos.VerifyResponse;
import com.aicoach.billing.repository.PaymentEventRepository;
import com.aicoach.billing.repository.SubscriptionRepository;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.user.domain.Plan;
import com.aicoach.user.domain.User;
import com.aicoach.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pro subscriptions. Checkout happens in the browser (Razorpay Checkout); the user's plan changes only when a
 * signature-verified webhook says so. Webhooks are idempotent by provider event id.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    public static final String PRO_MONTHLY = "PRO_MONTHLY";
    static final List<String> PRO_FEATURES = List.of("Unlimited interviews", "Voice interviews",
            "Company-specific question packs", "Priority evaluation");

    private final SubscriptionRepository subscriptions;
    private final PaymentEventRepository events;
    private final UserRepository users;
    private final PaymentProvider provider;
    private final BillingProperties properties;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public BillingService(SubscriptionRepository subscriptions, PaymentEventRepository events, UserRepository users,
            PaymentProvider provider, BillingProperties properties, AuditService audit, ObjectMapper json, Clock clock) {
        this.subscriptions = subscriptions;
        this.events = events;
        this.users = users;
        this.provider = provider;
        this.properties = properties;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    public List<PlanResponse> plans() {
        return List.of(new PlanResponse(PRO_MONTHLY, "Pro", properties.proPricePaise(), "INR", "MONTHLY", PRO_FEATURES));
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> current(UUID userId) {
        return subscriptions.findByUserIdOrderByCreatedAtDesc(userId).stream().findFirst().map(BillingService::view);
    }

    @Transactional
    public CheckoutResponse subscribe(UUID userId, String planId) {
        if (!PRO_MONTHLY.equals(planId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown plan");
        }
        User user = activeUser(userId);
        List<Subscription> existing = subscriptions.findByUserIdOrderByCreatedAtDesc(userId);
        if (existing.stream().anyMatch(Subscription::entitled)) {
            throw new AppException(ErrorCode.CONFLICT, "You already have an active Pro subscription");
        }
        // reuse a checkout that was started but not completed
        Optional<Subscription> pending = existing.stream().filter(s -> "CREATED".equals(s.getStatus())
                && s.getProvider().equals(provider.name())).findFirst();
        Subscription subscription = pending.orElseGet(() -> subscriptions.save(new Subscription(userId, provider.name(),
                provider.createSubscription(userId, user.getEmail()), planId)));
        return new CheckoutResponse(provider.name(), subscription.getProviderSubscriptionId(), provider.publicKeyId(),
                user.getName(), user.getEmail(), properties.proPricePaise());
    }

    /** Checks the checkout signature; the plan still changes only on the webhook. */
    @Transactional(readOnly = true)
    public VerifyResponse verify(UUID userId, String paymentId, String subscriptionId, String signature) {
        Subscription subscription = owned(userId, subscriptionId);
        if (!provider.verifyCheckout(paymentId, subscriptionId, signature)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid payment signature");
        }
        return new VerifyResponse(subscription.entitled() ? "ACTIVE" : "PENDING_WEBHOOK");
    }

    @Transactional
    public SubscriptionResponse cancel(UUID userId) {
        Subscription subscription = subscriptions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(s -> Subscription.OPEN.contains(s.getStatus())).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "No active subscription"));
        provider.cancel(subscription.getProviderSubscriptionId());
        if ("MOCK".equals(subscription.getProvider()) || "CREATED".equals(subscription.getStatus())) {
            // no provider event will follow: cancel immediately
            subscription.update("CANCELLED", null);
            subscriptions.save(subscription);
            syncPlan(userId, "cancelled");
        }
        return view(subscription);
    }

    /** MOCK mode only: simulates the provider's activation webhook for a subscription the user started. */
    @Transactional
    public SubscriptionResponse completeMock(UUID userId, String subscriptionId) {
        if (!properties.isMock()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Not available");
        }
        Subscription subscription = owned(userId, subscriptionId);
        Instant end = clock.instant().plus(java.time.Duration.ofDays(30));
        Map<String, Object> payload = Map.of("event", "subscription.activated", "mock", true, "payload",
                Map.of("subscription", Map.of("entity", Map.of("id", subscriptionId, "status", "active",
                        "current_end", end.getEpochSecond()))));
        handleEvent("mock_evt_" + UUID.randomUUID(), json.valueToTree(payload));
        return view(subscriptions.findById(subscription.getId()).orElseThrow());
    }

    /**
     * Verified webhook from the provider. Returns false if the signature is invalid (→ 400). Redeliveries of the same
     * event id are acknowledged without reprocessing.
     */
    @Transactional
    public boolean webhook(String rawBody, String signature, String eventId) {
        if (!provider.verifyWebhook(rawBody, signature)) {
            return false;
        }
        JsonNode event;
        try {
            event = json.readTree(rawBody);
        } catch (Exception e) {
            return false;
        }
        String id = eventId != null && !eventId.isBlank() ? eventId
                : event.path("id").asText(event.path("created_at").asText() + ":" + event.path("event").asText());
        handleEvent(id, event);
        return true;
    }

    private void handleEvent(String eventId, JsonNode event) {
        String type = event.path("event").asText("");
        if (!events.insertIfNew(provider.name(), eventId, type, event.toString())) {
            log.info("Duplicate payment event {} ignored", eventId);
            return;
        }
        String error = null;
        JsonNode entity = event.path("payload").path("subscription").path("entity");
        String subscriptionId = entity.path("id").asText(null);
        if (type.startsWith("subscription.") && subscriptionId != null) {
            Optional<Subscription> found = subscriptions.findByProviderSubscriptionId(subscriptionId);
            if (found.isPresent()) {
                Subscription subscription = found.get();
                String status = statusFor(type, entity.path("status").asText(""));
                Instant currentEnd = entity.hasNonNull("current_end")
                        ? Instant.ofEpochSecond(entity.get("current_end").asLong()) : null;
                if (status != null) {
                    subscription.update(status, currentEnd);
                    subscriptions.save(subscription);
                    syncPlan(subscription.getUserId(), type);
                }
            } else {
                error = "unknown subscription " + subscriptionId;
            }
        }
        events.markProcessed(eventId, clock.instant(), error);
    }

    static String statusFor(String eventType, String entityStatus) {
        return switch (eventType) {
            case "subscription.authenticated" -> "AUTHENTICATED";
            case "subscription.activated", "subscription.charged", "subscription.resumed" -> "ACTIVE";
            case "subscription.pending" -> "PENDING";
            case "subscription.halted" -> "HALTED";
            case "subscription.cancelled" -> "CANCELLED";
            case "subscription.completed" -> "COMPLETED";
            default -> entityStatus.isBlank() ? null : switch (entityStatus.toUpperCase(Locale.ROOT)) {
                case "CREATED", "AUTHENTICATED", "ACTIVE", "PENDING", "HALTED", "CANCELLED", "COMPLETED", "EXPIRED" ->
                        entityStatus.toUpperCase(Locale.ROOT);
                default -> null;
            };
        };
    }

    /** The user is Pro while any subscription is entitled. */
    private void syncPlan(UUID userId, String reason) {
        users.findById(userId).ifPresent(user -> {
            Plan target = subscriptions.findByUserIdOrderByCreatedAtDesc(userId).stream().anyMatch(Subscription::entitled)
                    ? Plan.PRO : Plan.FREE;
            if (user.getPlan() != target) {
                Plan before = user.getPlan();
                user.changePlan(target);
                users.save(user);
                audit.record(null, "PLAN_CHANGED", "USER", userId, Map.of("plan", before.name()),
                        Map.of("plan", target.name(), "reason", reason));
            }
        });
    }

    private Subscription owned(UUID userId, String providerSubscriptionId) {
        return subscriptions.findByProviderSubscriptionId(providerSubscriptionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Subscription not found"));
    }

    private User activeUser(UUID userId) {
        return users.findById(userId).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found"));
    }

    static SubscriptionResponse view(Subscription s) {
        return new SubscriptionResponse(s.getId(), s.getProvider(), s.getPlanId(), s.getStatus(), s.getCurrentEnd(),
                s.getCreatedAt());
    }
}
