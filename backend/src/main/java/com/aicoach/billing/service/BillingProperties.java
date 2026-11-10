package com.aicoach.billing.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.billing.*}. {@code provider=mock} simulates payments (refused with the prod profile). */
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(String provider, String razorpayKeyId, String razorpayKeySecret,
        String razorpayWebhookSecret, String razorpayPlanId, int proPricePaise) {

    public BillingProperties {
        provider = provider == null || provider.isBlank() ? "mock" : provider.trim().toLowerCase();
        proPricePaise = proPricePaise <= 0 ? 49_900 : proPricePaise;
    }

    public boolean isMock() {
        return "mock".equals(provider);
    }
}
