package com.leadsyncpro.billing.support;

import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
import com.leadsyncpro.billing.integration.iyzico.IyzicoInvoiceResponse;
import com.leadsyncpro.billing.integration.iyzico.IyzicoSubscriptionResponse;
import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.PaymentMethod;
import com.leadsyncpro.model.billing.Price;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Test-only implementation of {@link IyzicoClient} that communicates with a WireMock server.
 */
public class WireMockIyzicoClient implements IyzicoClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WireMockIyzicoClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    }

    @Override
    public String createOrAttachPaymentMethod(Customer customer, String cardToken) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customerReferenceCode", customer.getExternalId());
        payload.put("cardToken", cardToken);
        ResponseEntity<PaymentMethodResponse> response =
                restTemplate.postForEntity(baseUrl + "/v1/payment-methods", payload, PaymentMethodResponse.class);
        PaymentMethodResponse body = Objects.requireNonNull(response.getBody(), "WireMock response body is null");
        return body.token();
    }

    @Override
    public IyzicoSubscriptionResponse createSubscription(
            Customer customer, Price price, int seatCount, PaymentMethod paymentMethod) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customerReferenceCode", customer.getExternalId());
        payload.put("pricingPlanReferenceCode", price.getId() != null ? price.getId().toString() : null);
        payload.put("seatCount", seatCount);
        payload.put("paymentMethodToken", paymentMethod.getTokenRef());
        ResponseEntity<SubscriptionResponse> response =
                restTemplate.postForEntity(baseUrl + "/v1/subscriptions", payload, SubscriptionResponse.class);
        SubscriptionResponse body = Objects.requireNonNull(response.getBody(), "WireMock response body is null");
        return new IyzicoSubscriptionResponse(
                body.subscriptionId(), body.currentPeriodStart(), body.currentPeriodEnd(), body.cancelAtPeriodEnd());
    }

    @Override
    public void changeSubscriptionPlan(String externalSubscriptionId, Price newPrice, ProrationBehavior prorationBehavior) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", externalSubscriptionId);
        payload.put("newPricingPlan", newPrice.getId() != null ? newPrice.getId().toString() : null);
        payload.put("prorationBehavior", prorationBehavior.name());
        restTemplate.postForEntity(
                baseUrl + "/v1/subscriptions/" + externalSubscriptionId + "/plan", payload, Void.class);
    }

    @Override
    public void updateSeatCount(String externalSubscriptionId, int seatCount, ProrationBehavior prorationBehavior) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", externalSubscriptionId);
        payload.put("seatCount", seatCount);
        payload.put("prorationBehavior", prorationBehavior.name());
        restTemplate.postForEntity(
                baseUrl + "/v1/subscriptions/" + externalSubscriptionId + "/seats", payload, Void.class);
    }

    @Override
    public void cancelSubscription(String externalSubscriptionId, boolean cancelAtPeriodEnd) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", externalSubscriptionId);
        payload.put("cancelAtPeriodEnd", cancelAtPeriodEnd);
        restTemplate.postForEntity(
                baseUrl + "/v1/subscriptions/" + externalSubscriptionId + "/cancel", payload, Void.class);
    }

    @Override
    public IyzicoInvoiceResponse createInvoice(
            String externalSubscriptionId, Instant periodStart, Instant periodEnd, long amountCents, String currency) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", externalSubscriptionId);
        payload.put("periodStart", periodStart);
        payload.put("periodEnd", periodEnd);
        payload.put("amountCents", amountCents);
        payload.put("currency", currency);
        ResponseEntity<InvoiceResponse> response =
                restTemplate.postForEntity(baseUrl + "/v1/invoices", payload, InvoiceResponse.class);
        InvoiceResponse body = Objects.requireNonNull(response.getBody(), "WireMock response body is null");
        return new IyzicoInvoiceResponse(
                body.invoiceId(), body.periodStart(), body.periodEnd(), body.amountCents(), body.currency());
    }

    @Override
    public boolean verifyWebhook(String signatureHeader, String payload) {
        return true;
    }

    private record PaymentMethodResponse(String token) {}

    private record SubscriptionResponse(
            String subscriptionId, Instant currentPeriodStart, Instant currentPeriodEnd, boolean cancelAtPeriodEnd) {}

    private record InvoiceResponse(String invoiceId, Instant periodStart, Instant periodEnd, long amountCents, String currency) {}
}
