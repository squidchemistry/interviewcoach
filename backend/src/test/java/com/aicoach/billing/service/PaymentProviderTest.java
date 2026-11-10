package com.aicoach.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PaymentProviderTest {

    static final BillingProperties PROPS = new BillingProperties("razorpay", "rzp_test_key", "key_secret",
            "webhook_secret", "plan_123", 49_900);

    @Test
    void hmacMatchesTheKnownSha256Vector() {
        // RFC 4231-style sanity check with a simple key/message
        assertThat(PaymentProvider.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void razorpayVerifiesCheckoutAndWebhookSignatures() {
        RazorpayPaymentProvider provider = new RazorpayPaymentProvider(PROPS, new ObjectMapper());
        String checkout = PaymentProvider.hmacSha256Hex("key_secret", "pay_1|sub_1");
        assertThat(provider.verifyCheckout("pay_1", "sub_1", checkout)).isTrue();
        assertThat(provider.verifyCheckout("pay_1", "sub_2", checkout)).isFalse();
        assertThat(provider.verifyCheckout("pay_1", "sub_1", checkout.toUpperCase())).isTrue();

        String body = "{\"event\":\"subscription.activated\"}";
        assertThat(provider.verifyWebhook(body, PaymentProvider.hmacSha256Hex("webhook_secret", body))).isTrue();
        assertThat(provider.verifyWebhook(body + " ", PaymentProvider.hmacSha256Hex("webhook_secret", body))).isFalse();
        assertThat(provider.verifyWebhook(body, null)).isFalse();
    }

    @Test
    void razorpayRequiresAllCredentials() {
        assertThatThrownBy(() -> new RazorpayPaymentProvider(new BillingProperties("razorpay", "k", "", "w", "p", 1),
                new ObjectMapper())).isInstanceOf(IllegalStateException.class).hasMessageContaining("RAZORPAY_KEY_SECRET");
    }

    @Test
    void mockNeverAcceptsSignatures() {
        MockPaymentProvider mock = new MockPaymentProvider();
        assertThat(mock.verifyWebhook("{}", "x")).isFalse();
        assertThat(mock.verifyCheckout("a", "b", "c")).isFalse();
        assertThat(mock.createSubscription(java.util.UUID.randomUUID(), "a@b.c")).startsWith("mock_sub_");
    }

    @Test
    void mapsProviderEventsToStatuses() {
        assertThat(BillingService.statusFor("subscription.charged", "")).isEqualTo("ACTIVE");
        assertThat(BillingService.statusFor("subscription.halted", "")).isEqualTo("HALTED");
        assertThat(BillingService.statusFor("subscription.cancelled", "")).isEqualTo("CANCELLED");
        assertThat(BillingService.statusFor("subscription.updated", "active")).isEqualTo("ACTIVE");
        assertThat(BillingService.statusFor("subscription.updated", "weird")).isNull();
        assertThat(BillingService.statusFor("payment.captured", "")).isNull();
    }
}
