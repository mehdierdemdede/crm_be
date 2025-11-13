package com.leadsyncpro.billing.api;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.leadsyncpro.billing.support.WireMockIyzicoTestConfig;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WireMockIyzicoTestConfig.class)
class BillingE2ETest {

    private static final UUID CUSTOMER_ID = UUID.fromString("3d4e5f6a-7b8c-4d9e-0f1a-2b3c4d5e6f70");
    private static final String EXTERNAL_SUBSCRIPTION_ID = "sub_wiremock_123";
    private static final UUID INVOICE_ID = UUID.fromString("7b8c9d0e-1f2a-4b3c-5d6e-7f8091a2b3c4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        resetAllRequests();
        wireMockServer.resetAll();
    }

    @Test
    void listPublicPlans_isAccessibleWithoutAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(get("/billing/public/plans"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(payload.isArray()).isTrue();

        List<String> planCodes = StreamSupport.stream(payload.spliterator(), false)
                .map(node -> node.get("code").asText())
                .collect(Collectors.toList());
        assertThat(planCodes).contains("BASIC", "PRO");

        JsonNode basicPlan = StreamSupport.stream(payload.spliterator(), false)
                .filter(node -> "BASIC".equals(node.get("code").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("BASIC plan not found"));

        assertThat(basicPlan.get("prices").isArray()).isTrue();
        assertThat(basicPlan.get("features").isArray()).isTrue();
        assertThat(basicPlan.get("metadata").get("perSeatPrice_month").decimalValue()).isEqualByComparingTo("15");
        JsonNode firstPrice = basicPlan.get("prices").get(0);
        assertThat(firstPrice.get("currency").asText()).isEqualTo("TRY");
        assertThat(firstPrice.get("amount").decimalValue()).isEqualByComparingTo("15");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getInvoiceDetails_returnsInvoicePayload() throws Exception {
        mockMvc.perform(get("/billing/invoices/" + INVOICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(INVOICE_ID.toString()))
                .andExpect(jsonPath("$.subtotalCents").value(99000))
                .andExpect(jsonPath("$.taxCents").value(18810))
                .andExpect(jsonPath("$.externalInvoiceId").value("inv_demo_001"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void tokenizePaymentMethod_returnsGatewayToken() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/v1/payment-method-tokens"))
                .willReturn(okJson("{\"token\":\"tok_wiremock_456\"}")));

        Map<String, Object> request = Map.of(
                "cardHolderName", "Ada Lovelace",
                "cardNumber", "5528790000000008",
                "expireMonth", "01",
                "expireYear", "2026",
                "cvc", "123");

        mockMvc.perform(post("/payment-methods/tokenize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok_wiremock_456"));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment-method-tokens")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void subscriptionLifecycle_withWireMockedIyziGateway() throws Exception {
        stubIyziPaymentFlow();

        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("customerId", CUSTOMER_ID.toString());
        createRequest.put("planCode", "BASIC");
        createRequest.put("billingPeriod", "MONTH");
        createRequest.put("seatCount", 5);
        createRequest.put("trialDays", 0);
        createRequest.put("cardToken", "tok_demo_123");

        MvcResult createResult = mockMvc.perform(post("/billing/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planCode").value("BASIC"))
                .andExpect(jsonPath("$.billingPeriod").value("MONTH"))
                .andExpect(jsonPath("$.seatCount").value(5))
                .andReturn();

        JsonNode createPayload = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID subscriptionId = UUID.fromString(createPayload.get("id").asText());
        assertThat(createPayload.get("externalSubscriptionId").asText()).isEqualTo(EXTERNAL_SUBSCRIPTION_ID);

        Map<String, Object> changePlanRequest = Map.of(
                "planCode", "PRO",
                "billingPeriod", "YEAR",
                "proration", "IMMEDIATE");

        mockMvc.perform(post("/billing/subscriptions/" + subscriptionId + "/change-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePlanRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PRO"))
                .andExpect(jsonPath("$.billingPeriod").value("YEAR"));

        Map<String, Object> seatUpdateRequest = Map.of("seatCount", 15, "proration", "IMMEDIATE");

        mockMvc.perform(post("/billing/subscriptions/" + subscriptionId + "/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatUpdateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatCount").value(15));

        Map<String, Object> cancelRequest = Map.of("cancelAtPeriodEnd", true);

        mockMvc.perform(post("/billing/subscriptions/" + subscriptionId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/billing/subscriptions/" + subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));

        mockMvc.perform(get("/billing/customers/" + CUSTOMER_ID + "/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("TRY"));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payment-methods")));
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/subscriptions")));
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/subscriptions/" + EXTERNAL_SUBSCRIPTION_ID + "/plan")));
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/subscriptions/" + EXTERNAL_SUBSCRIPTION_ID + "/seats")));
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/subscriptions/" + EXTERNAL_SUBSCRIPTION_ID + "/cancel")));
    }

    private void stubIyziPaymentFlow() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/payment-methods"))
                .willReturn(okJson("{\"token\":\"pm_wiremock_token\"}")));

        String subscriptionResponse = String.format(
                "{\"subscriptionId\":\"%s\",\"currentPeriodStart\":\"%s\",\"currentPeriodEnd\":\"%s\",\"cancelAtPeriodEnd\":false}",
                EXTERNAL_SUBSCRIPTION_ID,
                Instant.parse("2024-02-01T00:00:00Z"),
                Instant.parse("2024-02-29T23:59:59Z"));

        wireMockServer.stubFor(post(urlEqualTo("/v1/subscriptions"))
                .willReturn(okJson(subscriptionResponse)));

        wireMockServer.stubFor(post(urlEqualTo("/v1/subscriptions/" + EXTERNAL_SUBSCRIPTION_ID + "/plan"))
                .willReturn(ok()));

        wireMockServer.stubFor(post(urlEqualTo("/v1/subscriptions/" + EXTERNAL_SUBSCRIPTION_ID + "/seats"))
                .willReturn(ok()));

        wireMockServer.stubFor(post(urlEqualTo("/v1/subscriptions/" + EXTERNAL_SUBSCRIPTION_ID + "/cancel"))
                .willReturn(ok()));
    }
}
