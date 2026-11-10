package com.aicoach.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Billing bodies ({@code /api/v1/billing}). */
public final class BillingDtos {

    private BillingDtos() {
    }

    public record PlanResponse(String id, String name, int priceInPaise, String currency, String interval,
            List<String> features) {
    }

    public record SubscriptionResponse(UUID id, String provider, String planId, String status, Instant currentEnd,
            Instant createdAt) {
    }

    /**
     * {@code provider}: RAZORPAY → open Razorpay Checkout with {@code keyId} + {@code subscriptionId};
     * MOCK → no real payment: call {@code POST /billing/mock/complete} to simulate the provider's webhook.
     */
    public record CheckoutResponse(String provider, String subscriptionId, String keyId, String name, String email,
            int priceInPaise) {
    }

    public record CreateSubscriptionRequest(@NotBlank @Pattern(regexp = "PRO_MONTHLY") String planId) {
    }

    public record VerifyRequest(@NotBlank @Size(max = 64) String paymentId, @NotBlank @Size(max = 64) String subscriptionId,
            @NotBlank @Size(max = 256) String signature) {
    }

    /** {@code status}: PENDING_WEBHOOK (signature ok, plan changes when the webhook arrives) or ACTIVE. */
    public record VerifyResponse(String status) {
    }

    public record MockCompleteRequest(@NotBlank @Size(max = 64) String subscriptionId) {
    }
}
