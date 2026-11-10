package com.aicoach.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Factory: picks the payment provider from {@code app.billing.provider}. */
@Configuration(proxyBeanMethods = false)
public class BillingConfig {

    @Bean
    PaymentProvider paymentProvider(BillingProperties properties, ObjectMapper json, Environment environment) {
        return switch (properties.provider()) {
            case "razorpay" -> new RazorpayPaymentProvider(properties, json);
            case "mock" -> {
                if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
                    throw new IllegalStateException("PAYMENTS_PROVIDER=mock is not allowed with the prod profile");
                }
                yield new MockPaymentProvider();
            }
            default -> throw new IllegalStateException("Unknown PAYMENTS_PROVIDER: " + properties.provider());
        };
    }
}
