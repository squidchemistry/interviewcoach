package com.aicoach.billing.web;

import com.aicoach.billing.dto.BillingDtos.CheckoutResponse;
import com.aicoach.billing.dto.BillingDtos.CreateSubscriptionRequest;
import com.aicoach.billing.dto.BillingDtos.MockCompleteRequest;
import com.aicoach.billing.dto.BillingDtos.PlanResponse;
import com.aicoach.billing.dto.BillingDtos.SubscriptionResponse;
import com.aicoach.billing.dto.BillingDtos.VerifyRequest;
import com.aicoach.billing.dto.BillingDtos.VerifyResponse;
import com.aicoach.billing.service.BillingService;
import com.aicoach.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Billing")
public class BillingController {

    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/billing/plans")
    @Operation(summary = "Available plans (public)")
    public List<PlanResponse> plans() {
        return billing.plans();
    }

    @GetMapping("/billing/subscription")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "My latest subscription (204 if none)")
    public ResponseEntity<SubscriptionResponse> subscription(@AuthenticationPrincipal Jwt jwt) {
        return billing.current(AuthenticatedUser.id(jwt)).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/billing/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Start a Pro subscription checkout")
    public CheckoutResponse subscribe(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return billing.subscribe(AuthenticatedUser.id(jwt), request.planId());
    }

    @PostMapping("/billing/subscriptions/verify")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Verify the checkout signature (the plan changes on the provider's webhook)")
    public VerifyResponse verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VerifyRequest request) {
        return billing.verify(AuthenticatedUser.id(jwt), request.paymentId(), request.subscriptionId(),
                request.signature());
    }

    @PostMapping("/billing/subscriptions/cancel")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancel at the end of the billing cycle")
    public SubscriptionResponse cancel(@AuthenticationPrincipal Jwt jwt) {
        return billing.cancel(AuthenticatedUser.id(jwt));
    }

    @PostMapping("/billing/mock/complete")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "MOCK payments only: simulate the provider's activation webhook (404 otherwise)")
    public SubscriptionResponse completeMock(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MockCompleteRequest request) {
        return billing.completeMock(AuthenticatedUser.id(jwt), request.subscriptionId());
    }

    @PostMapping("/webhooks/razorpay")
    @Operation(summary = "Razorpay webhook (HMAC-SHA256 of the raw body in X-Razorpay-Signature)")
    public ResponseEntity<Void> webhook(@RequestBody String body,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        return billing.webhook(body, signature, eventId) ? ResponseEntity.ok().build()
                : ResponseEntity.badRequest().build();
    }
}
