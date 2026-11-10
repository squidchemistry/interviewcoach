package com.aicoach.billing.service;

import java.util.UUID;

/**
 * MOCK payments (PAYMENTS_PROVIDER=mock): no money moves. Subscriptions are activated through
 * {@code POST /billing/mock/complete}, which feeds a synthetic, clearly marked event into the same handler a real
 * webhook uses. Refused with the prod profile (see {@link BillingConfig}).
 */
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public String publicKeyId() {
        return null;
    }

    @Override
    public String createSubscription(UUID userId, String email) {
        return "mock_sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    @Override
    public boolean verifyCheckout(String paymentId, String subscriptionId, String signature) {
        return false; // there is no checkout in mock mode
    }

    @Override
    public boolean verifyWebhook(String rawBody, String signature) {
        return false; // mock mode never accepts external webhooks
    }

    @Override
    public void cancel(String subscriptionId) {
        // nothing to cancel at a provider
    }
}
