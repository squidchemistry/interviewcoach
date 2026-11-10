package com.aicoach.billing.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Strategy over payment providers (Razorpay, or the clearly labelled mock). The plan of a user only ever changes from
 * a verified provider event, never from the browser's word.
 */
public interface PaymentProvider {

    /** "RAZORPAY" or "MOCK" (stored on subscriptions). */
    String name();

    /** Public key the browser needs to open checkout (null for mock). */
    String publicKeyId();

    /** Creates the subscription at the provider; returns its id. */
    String createSubscription(UUID userId, String email);

    /** Checkout callback signature (HMAC of {@code paymentId|subscriptionId}). */
    boolean verifyCheckout(String paymentId, String subscriptionId, String signature);

    /** Webhook signature (HMAC of the raw body). */
    boolean verifyWebhook(String rawBody, String signature);

    void cancel(String subscriptionId);

    static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Constant-time comparison of hex signatures. */
    static boolean signatureMatches(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.US_ASCII),
                actualHex.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
