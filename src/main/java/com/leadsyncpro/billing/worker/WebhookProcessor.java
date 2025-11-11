package com.leadsyncpro.billing.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.model.billing.Invoice;
import com.leadsyncpro.model.billing.InvoiceStatus;
import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import com.leadsyncpro.model.billing.WebhookEvent;
import com.leadsyncpro.model.billing.WebhookEventStatus;
import com.leadsyncpro.repository.billing.InvoiceRepository;
import com.leadsyncpro.repository.billing.SubscriptionRepository;
import com.leadsyncpro.repository.billing.WebhookEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessor {

    private static final String SUBSCRIPTION_ID = "subscriptionId";

    private final WebhookEventRepository webhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @Retryable(
            value = WebhookProcessingException.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 1_000, multiplier = 2.0))
    public void process(UUID eventId) {
        WebhookEvent event = webhookEventRepository
                .findById(eventId)
                .orElseThrow(() -> new WebhookProcessingException("Webhook event not found: " + eventId));

        if (event.getStatus() == WebhookEventStatus.PROCESSED) {
            log.info("Webhook event {} already processed", eventId);
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            handleEvent(event, payload);
            event.setStatus(WebhookEventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
        } catch (WebhookProcessingException ex) {
            log.warn("Retryable error while processing webhook {}: {}", eventId, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error while processing webhook {}", eventId, ex);
            throw new WebhookProcessingException("Failed to process webhook " + eventId, ex);
        }
    }

    @Recover
    public void recover(WebhookProcessingException exception, UUID eventId) {
        webhookEventRepository
                .findById(eventId)
                .ifPresent(event -> {
                    event.setStatus(WebhookEventStatus.FAILED);
                    event.setProcessedAt(Instant.now());
                    webhookEventRepository.save(event);
                });
        log.error("Failed to process webhook {} after retries: {}", eventId, exception.getMessage(), exception);
    }

    private void handleEvent(WebhookEvent event, JsonNode payload) {
        String eventType = event.getEventType();
        log.info("Processing Iyzico webhook event type={} id={}", eventType, event.getProviderEventId());
        switch (eventType) {
            case "payment.succeeded" -> handlePaymentSucceeded(payload);
            case "payment.failed" -> handlePaymentFailed(payload);
            case "subscription.renewed" -> handleSubscriptionRenewed(payload);
            case "subscription.canceled" -> handleSubscriptionCanceled(payload);
            case "invoice.paid" -> handleInvoicePaid(payload);
            case "invoice.payment_failed" -> handleInvoicePaymentFailed(payload);
            default -> log.warn("Received unknown Iyzico webhook event type={}", eventType);
        }
    }

    private void handlePaymentSucceeded(JsonNode payload) {
        findSubscription(payload)
                .ifPresentOrElse(
                        subscription -> {
                            subscription.setStatus(SubscriptionStatus.ACTIVE);
                            subscription.setCancelAtPeriodEnd(false);
                            subscriptionRepository.save(subscription);
                        },
                        () -> log.warn("Subscription not found for payment.succeeded payload"));
    }

    private void handlePaymentFailed(JsonNode payload) {
        findSubscription(payload)
                .ifPresentOrElse(
                        subscription -> {
                            subscription.setStatus(SubscriptionStatus.PAST_DUE);
                            subscriptionRepository.save(subscription);
                        },
                        () -> log.warn("Subscription not found for payment.failed payload"));
    }

    private void handleSubscriptionRenewed(JsonNode payload) {
        Subscription subscription = requireSubscription(payload);
        extractInstant(payload, "currentPeriodStart", "periodStart", "startDate")
                .ifPresent(subscription::setCurrentPeriodStart);
        extractInstant(payload, "currentPeriodEnd", "periodEnd", "endDate")
                .ifPresent(subscription::setCurrentPeriodEnd);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
    }

    private void handleSubscriptionCanceled(JsonNode payload) {
        Subscription subscription = requireSubscription(payload);
        boolean cancelAtPeriodEnd = extractBoolean(payload, "cancelAtPeriodEnd", "cancel_at_period_end")
                .orElse(true);
        subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        if (!cancelAtPeriodEnd) {
            subscription.setStatus(SubscriptionStatus.CANCELED);
        }
        subscriptionRepository.save(subscription);
    }

    private void handleInvoicePaid(JsonNode payload) {
        Invoice invoice = requireInvoice(payload);
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);
        findSubscription(payload)
                .ifPresent(subscription -> {
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscriptionRepository.save(subscription);
                });
    }

    private void handleInvoicePaymentFailed(JsonNode payload) {
        Invoice invoice = requireInvoice(payload);
        invoice.setStatus(InvoiceStatus.OPEN);
        invoiceRepository.save(invoice);
        findSubscription(payload)
                .ifPresent(subscription -> {
                    subscription.setStatus(SubscriptionStatus.PAST_DUE);
                    subscriptionRepository.save(subscription);
                });
    }

    private Subscription requireSubscription(JsonNode payload) {
        return findSubscription(payload)
                .orElseThrow(() -> new WebhookProcessingException("Subscription not found in payload"));
    }

    private Optional<Subscription> findSubscription(JsonNode payload) {
        return extractText(payload, SUBSCRIPTION_ID, "subscriptionReferenceCode", "subscription_code")
                .flatMap(subscriptionRepository::findByExternalSubscriptionId);
    }

    private Invoice requireInvoice(JsonNode payload) {
        return findInvoice(payload)
                .orElseThrow(() -> new WebhookProcessingException("Invoice not found in payload"));
    }

    private Optional<Invoice> findInvoice(JsonNode payload) {
        return extractText(payload, "invoiceId", "invoiceReferenceCode", "invoice_code")
                .flatMap(invoiceRepository::findByExternalInvoiceId);
    }

    private Optional<String> extractText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                JsonNode valueNode = node.get(fieldName);
                if (valueNode.isTextual() || valueNode.isIntegralNumber() || valueNode.isFloatingPointNumber()) {
                    String value = valueNode.asText();
                    if (StringUtils.hasText(value)) {
                        return Optional.of(value);
                    }
                }
            }
        }
        String[] containers = {"data", "subscription", "invoice", "payment", "event"};
        for (String container : containers) {
            if (node.has(container)) {
                Optional<String> nested = extractText(node.get(container), fieldNames);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Boolean> extractBoolean(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                JsonNode valueNode = node.get(fieldName);
                if (valueNode.isBoolean()) {
                    return Optional.of(valueNode.asBoolean());
                }
                if (valueNode.isTextual()) {
                    String text = valueNode.asText();
                    if ("true".equalsIgnoreCase(text)) {
                        return Optional.of(Boolean.TRUE);
                    }
                    if ("false".equalsIgnoreCase(text)) {
                        return Optional.of(Boolean.FALSE);
                    }
                }
            }
        }
        String[] containers = {"data", "subscription", "invoice", "payment", "event"};
        for (String container : containers) {
            if (node.has(container)) {
                Optional<Boolean> nested = extractBoolean(node.get(container), fieldNames);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Instant> extractInstant(JsonNode node, String... fieldNames) {
        return extractText(node, fieldNames).flatMap(this::parseInstant);
    }

    private Optional<Instant> parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (Exception ignored) {
            // ignored
        }
        try {
            long epoch = Long.parseLong(value);
            if (value.length() <= 10) {
                return Optional.of(Instant.ofEpochSecond(epoch));
            }
            return Optional.of(Instant.ofEpochMilli(epoch));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
