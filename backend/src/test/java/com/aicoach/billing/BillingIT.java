package com.aicoach.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aicoach.support.AuthTestClient;
import com.aicoach.support.AuthTestClient.Session;
import com.aicoach.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/** PAYMENTS_PROVIDER=mock: the plan changes only through the (simulated) provider event. */
@IntegrationTest
class BillingIT {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode call(RequestBuilder builder, int expected) throws Exception {
        var response = mvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }

    @Test
    void mockCheckoutActivatesProAndCancelReturnsToFree() throws Exception {
        JsonNode plans = call(get("/api/v1/billing/plans"), 200);
        assertThat(plans.get(0).get("priceInPaise").asInt()).isEqualTo(49_900);

        Session s = new AuthTestClient(mvc).register(AuthTestClient.uniqueEmail());
        call(get("/api/v1/billing/subscription").header(HttpHeaders.AUTHORIZATION, s.bearer()), 204);

        JsonNode checkout = call(post("/api/v1/billing/subscriptions").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"planId\":\"PRO_MONTHLY\"}"), 201);
        assertThat(checkout.get("provider").asText()).isEqualTo("MOCK");
        String subscriptionId = checkout.get("subscriptionId").asText();
        assertThat(call(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200).get("plan").asText())
                .isEqualTo("FREE");

        // the browser cannot self-verify a payment in mock mode
        call(post("/api/v1/billing/subscriptions/verify").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":\"pay_1\",\"subscriptionId\":\"" + subscriptionId + "\",\"signature\":\"x\"}"), 400);

        JsonNode active = call(post("/api/v1/billing/mock/complete").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"subscriptionId\":\"" + subscriptionId + "\"}"), 200);
        assertThat(active.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(call(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200).get("plan").asText())
                .isEqualTo("PRO");
        call(post("/api/v1/billing/subscriptions").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"planId\":\"PRO_MONTHLY\"}"), 409);

        JsonNode cancelled = call(post("/api/v1/billing/subscriptions/cancel").header(HttpHeaders.AUTHORIZATION, s.bearer()),
                200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(call(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200).get("plan").asText())
                .isEqualTo("FREE");
    }

    @Test
    void webhooksWithoutAValidSignatureAreRejected() throws Exception {
        call(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "deadbeef").content("{\"event\":\"subscription.activated\"}"), 400);
    }

    @Test
    void anotherUsersSubscriptionCannotBeCompleted() throws Exception {
        AuthTestClient auth = new AuthTestClient(mvc);
        Session owner = auth.register(AuthTestClient.uniqueEmail());
        Session other = auth.register(AuthTestClient.uniqueEmail());
        String id = call(post("/api/v1/billing/subscriptions").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"planId\":\"PRO_MONTHLY\"}"), 201)
                .get("subscriptionId").asText();
        call(post("/api/v1/billing/mock/complete").header(HttpHeaders.AUTHORIZATION, other.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"subscriptionId\":\"" + id + "\"}"), 404);
    }
}
