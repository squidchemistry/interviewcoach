package com.aicoach.billing.service;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** Razorpay Subscriptions over its REST API (basic auth with key id + secret). */
public class RazorpayPaymentProvider implements PaymentProvider {

    static final String API = "https://api.razorpay.com/v1";

    private final BillingProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;
    private final String baseUrl;

    public RazorpayPaymentProvider(BillingProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), API);
    }

    RazorpayPaymentProvider(BillingProperties properties, ObjectMapper json, HttpClient http, String baseUrl) {
        for (String value : new String[] {properties.razorpayKeyId(), properties.razorpayKeySecret(),
                properties.razorpayWebhookSecret(), properties.razorpayPlanId()}) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("PAYMENTS_PROVIDER=razorpay needs RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, "
                        + "RAZORPAY_WEBHOOK_SECRET and RAZORPAY_PLAN_ID");
            }
        }
        this.properties = properties;
        this.json = json;
        this.http = http;
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() {
        return "RAZORPAY";
    }

    @Override
    public String publicKeyId() {
        return properties.razorpayKeyId();
    }

    @Override
    public String createSubscription(UUID userId, String email) {
        JsonNode response = post("/subscriptions", Map.of("plan_id", properties.razorpayPlanId(), "total_count", 12,
                "customer_notify", 1, "notes", Map.of("userId", userId.toString())));
        String id = response.path("id").asText(null);
        if (id == null) {
            throw new AppException(ErrorCode.CONFLICT, "The payment provider did not return a subscription");
        }
        return id;
    }

    @Override
    public boolean verifyCheckout(String paymentId, String subscriptionId, String signature) {
        return PaymentProvider.signatureMatches(
                PaymentProvider.hmacSha256Hex(properties.razorpayKeySecret(), paymentId + "|" + subscriptionId), signature);
    }

    @Override
    public boolean verifyWebhook(String rawBody, String signature) {
        return PaymentProvider.signatureMatches(
                PaymentProvider.hmacSha256Hex(properties.razorpayWebhookSecret(), rawBody), signature);
    }

    @Override
    public void cancel(String subscriptionId) {
        post("/subscriptions/" + subscriptionId + "/cancel", Map.of("cancel_at_cycle_end", 1));
    }

    private JsonNode post(String path, Object body) {
        String auth = Base64.getEncoder().encodeToString((properties.razorpayKeyId() + ":" + properties.razorpayKeySecret())
                .getBytes(StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AppException(ErrorCode.CONFLICT, "The payment provider rejected the request ("
                        + response.statusCode() + ")");
            }
            return json.readTree(response.body());
        } catch (AppException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.FEATURE_DISABLED, "The payment provider is unavailable");
        } catch (Exception e) {
            throw new AppException(ErrorCode.FEATURE_DISABLED, "The payment provider is unavailable");
        }
    }
}
