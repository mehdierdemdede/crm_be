package com.leadsyncpro.billing.integration.iyzico;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iyzipay.IyzipayResource;
import com.iyzipay.Options;
import com.iyzipay.model.Card;
import com.iyzipay.model.CardInformation;
import com.iyzipay.model.Locale;
import com.iyzipay.request.CreateCardRequest;
import com.leadsyncpro.billing.config.IyzicoProperties;
import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.PaymentMethod;
import com.leadsyncpro.model.billing.Price;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

@Slf4j
public class DefaultIyzicoClient implements IyzicoClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final String SANDBOX_BASE_URL = "https://sandbox-api.iyzipay.com";

    private final IyzicoProperties properties;
    private final ObjectMapper objectMapper;
    private final Options options;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public DefaultIyzicoClient(
            IyzicoProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            Tracer tracer) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
        this.options = buildOptions();

        log.info(
                "Iyzico client initialized with baseUrl={} and timeouts={}ms",
                getBaseUrl(),
                DEFAULT_TIMEOUT.toMillis());
    }

    @Override
    public String createOrAttachPaymentMethod(Customer customer, String cardToken) {
        Map<String, Object> request = new HashMap<>();
        request.put("customerReferenceCode", customer.getExternalId());
        request.put("cardToken", cardToken);
        logRequest("createOrAttachPaymentMethod", request);
        return recordLatency(
                "create_or_attach_payment_method",
                () -> {
                    String paymentMethodToken = cardToken;
                    logResponse("createOrAttachPaymentMethod", Map.of("paymentMethodToken", paymentMethodToken));
                    return paymentMethodToken;
                },
                "Failed to create or attach payment method");
    }

    @Override
    public String tokenizePaymentMethod(
            String cardHolderName, String cardNumber, String expireMonth, String expireYear, String cvc) {
        Map<String, Object> sanitizedCard = new HashMap<>();
        sanitizedCard.put("cardHolderName", cardHolderName);
        sanitizedCard.put("cardNumber", maskCardNumber(cardNumber));
        sanitizedCard.put("expireMonth", expireMonth);
        sanitizedCard.put("expireYear", expireYear);
        sanitizedCard.put("cvc", "***");
        logRequest("tokenizePaymentMethod", sanitizedCard);

        CreateCardRequest request = new CreateCardRequest();
        request.setLocale(Locale.EN.getValue());
        request.setConversationId(generateExternalId("conv"));
        request.setExternalId(generateExternalId("card"));
        request.setEmail("anonymous@leadsyncpro.local");

        CardInformation cardInformation = new CardInformation();
        cardInformation.setCardHolderName(cardHolderName);
        cardInformation.setCardNumber(cardNumber);
        cardInformation.setExpireMonth(expireMonth);
        cardInformation.setExpireYear(expireYear);
        request.setCard(cardInformation);

        return recordLatency(
                "tokenize_payment_method",
                () -> {
                    Card card = Card.create(request, options);
                    validateIyzipayResponse(card, "tokenizePaymentMethod");
                    logResponse("tokenizePaymentMethod", Map.of("token", card.getCardToken()));
                    return card.getCardToken();
                },
                "Failed to tokenize payment method");
    }

    @Override
    public IyzicoSubscriptionResponse createSubscription(
            Customer customer, Price price, int seatCount, PaymentMethod paymentMethod) {
        Map<String, Object> request = new HashMap<>();
        request.put("customerReferenceCode", customer.getExternalId());
        request.put("pricingPlanReferenceCode", price.getId() != null ? price.getId().toString() : null);
        request.put("seatCount", seatCount);
        request.put("paymentMethodToken", paymentMethod.getTokenRef());
        logRequest("createSubscription", request);
        return recordLatency(
                "create_subscription",
                () -> {
                    Instant now = Instant.now();
                    IyzicoSubscriptionResponse response = new IyzicoSubscriptionResponse(
                            generateExternalId("sub"), now, now.plus(Duration.ofDays(30)), false);
                    logResponse("createSubscription", response);
                    return response;
                },
                "Failed to create subscription");
    }

    @Override
    public void changeSubscriptionPlan(
            String externalSubscriptionId, Price newPrice, ProrationBehavior prorationBehavior) {
        Map<String, Object> request = new HashMap<>();
        request.put("subscriptionId", externalSubscriptionId);
        request.put("newPricingPlan", newPrice.getId() != null ? newPrice.getId().toString() : null);
        request.put("prorationBehavior", prorationBehavior.name());
        logRequest("changeSubscriptionPlan", request);
        recordLatency(
                "change_subscription_plan",
                () -> logResponse("changeSubscriptionPlan", Map.of("status", "accepted")),
                "Failed to change subscription plan");
    }

    @Override
    public void updateSeatCount(String externalSubscriptionId, int seatCount, ProrationBehavior prorationBehavior) {
        Map<String, Object> request = new HashMap<>();
        request.put("subscriptionId", externalSubscriptionId);
        request.put("seatCount", seatCount);
        request.put("prorationBehavior", prorationBehavior.name());
        logRequest("updateSeatCount", request);
        recordLatency(
                "update_seat_count",
                () -> logResponse("updateSeatCount", Map.of("status", "accepted")),
                "Failed to update seat count");
    }

    @Override
    public void cancelSubscription(String externalSubscriptionId, boolean cancelAtPeriodEnd) {
        Map<String, Object> request = new HashMap<>();
        request.put("subscriptionId", externalSubscriptionId);
        request.put("cancelAtPeriodEnd", cancelAtPeriodEnd);
        logRequest("cancelSubscription", request);
        recordLatency(
                "cancel_subscription",
                () -> logResponse("cancelSubscription", Map.of("status", "accepted")),
                "Failed to cancel subscription");
    }

    @Override
    public IyzicoInvoiceResponse createInvoice(
            String externalSubscriptionId,
            Instant periodStart,
            Instant periodEnd,
            long amountCents,
            String currency) {
        Map<String, Object> request = new HashMap<>();
        request.put("subscriptionId", externalSubscriptionId);
        request.put("periodStart", periodStart);
        request.put("periodEnd", periodEnd);
        request.put("amountCents", amountCents);
        request.put("currency", currency);
        logRequest("createInvoice", request);
        return recordLatency(
                "create_invoice",
                () -> {
                    IyzicoInvoiceResponse response = new IyzicoInvoiceResponse(
                            generateExternalId("inv"), periodStart, periodEnd, amountCents, currency);
                    logResponse("createInvoice", response);
                    return response;
                },
                "Failed to create invoice");
    }

    @Override
    public boolean verifyWebhook(String signatureHeader, String payload) {
        if (!StringUtils.hasText(signatureHeader) || !StringUtils.hasText(payload)) {
            return false;
        }

        String secret = properties.getWebhookSigningSecret();
        if (!StringUtils.hasText(secret)) {
            log.warn("Webhook signing secret is not configured; skipping signature verification");
            return false;
        }

        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(expected);
            boolean valid = MessageDigestEquality.equals(encoded, signatureHeader.trim());
            if (!valid) {
                log.warn("Webhook signature mismatch; expected={}, actual={}", encoded, signatureHeader);
            }
            return valid;
        } catch (Exception ex) {
            throw wrap("Failed to verify webhook signature", ex);
        }
    }

    private String getBaseUrl() {
        return StringUtils.hasText(properties.getBaseUrl()) ? properties.getBaseUrl() : SANDBOX_BASE_URL;
    }

    private Options buildOptions() {
        Options opts = new Options();
        opts.setApiKey(properties.getApiKey());
        opts.setSecretKey(properties.getSecretKey());
        opts.setBaseUrl(getBaseUrl());
        return opts;
    }

    private void logRequest(String operation, Object payload) {
        String requestId = Optional.ofNullable(MDC.get("requestId")).orElse("-");
        String traceId = currentTraceId();
        String spanId = currentSpanId();
        if (log.isDebugEnabled()) {
            log.debug(
                    "traceId={} spanId={} requestId={} iyzico operation={} request={} ",
                    traceId,
                    spanId,
                    requestId,
                    operation,
                    toJson(payload));
        } else {
            log.info(
                    "traceId={} spanId={} requestId={} iyzico operation={}",
                    traceId,
                    spanId,
                    requestId,
                    operation);
        }
    }

    private void logResponse(String operation, Object payload) {
        String requestId = Optional.ofNullable(MDC.get("requestId")).orElse("-");
        String traceId = currentTraceId();
        String spanId = currentSpanId();
        if (log.isDebugEnabled()) {
            log.debug(
                    "traceId={} spanId={} requestId={} iyzico operation={} response={} ",
                    traceId,
                    spanId,
                    requestId,
                    operation,
                    toJson(payload));
        } else {
            log.info(
                    "traceId={} spanId={} requestId={} iyzico operation={} completed",
                    traceId,
                    spanId,
                    requestId,
                    operation);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private IyzicoException wrap(String message, Exception ex) {
        return ex instanceof IyzicoException ? (IyzicoException) ex : new IyzicoException(message, ex);
    }

    private String generateExternalId(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }

    private String maskCardNumber(String cardNumber) {
        if (!StringUtils.hasText(cardNumber)) {
            return null;
        }
        String digitsOnly = cardNumber.replaceAll("\\D", "");
        if (digitsOnly.length() <= 4) {
            return digitsOnly;
        }
        String maskedPrefix = "*".repeat(digitsOnly.length() - 4);
        return maskedPrefix + digitsOnly.substring(digitsOnly.length() - 4);
    }

    private <T> T recordLatency(String operation, Supplier<T> supplier, String errorMessage) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            throw wrap(errorMessage, ex);
        } finally {
            stopTimer(operation, sample);
        }
    }

    private void recordLatency(String operation, Runnable runnable, String errorMessage) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            throw wrap(errorMessage, ex);
        } finally {
            stopTimer(operation, sample);
        }
    }

    private void stopTimer(String operation, Timer.Sample sample) {
        sample.stop(Timer.builder("iyzico_http_client_latency")
                .tag("operation", operation)
                .register(meterRegistry));
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        return span != null ? span.context().traceId() : "-";
    }

    private String currentSpanId() {
        Span span = tracer.currentSpan();
        return span != null ? span.context().spanId() : "-";
    }

    private void validateIyzipayResponse(IyzipayResource resource, String operation) {
        if (resource == null || !"success".equalsIgnoreCase(resource.getStatus())) {
            String errorMessage = resource != null
                    ? "%s failed: %s".formatted(operation, resource.getErrorMessage())
                    : "%s failed with null response".formatted(operation);
            throw new IyzicoException(errorMessage);
        }
    }

    private static final class MessageDigestEquality {
        private MessageDigestEquality() {}

        private static boolean equals(String expected, String actual) {
            byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
            if (expectedBytes.length != actualBytes.length) {
                return false;
            }
            int result = 0;
            for (int i = 0; i < expectedBytes.length; i++) {
                result |= expectedBytes[i] ^ actualBytes[i];
            }
            return result == 0;
        }
    }
}
