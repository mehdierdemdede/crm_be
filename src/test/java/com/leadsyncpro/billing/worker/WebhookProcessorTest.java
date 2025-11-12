package com.leadsyncpro.billing.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadsyncpro.billing.metrics.SubscriptionStatusMetrics;
import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import com.leadsyncpro.model.billing.WebhookEvent;
import com.leadsyncpro.model.billing.WebhookEventStatus;
import com.leadsyncpro.repository.billing.InvoiceRepository;
import com.leadsyncpro.repository.billing.SubscriptionRepository;
import com.leadsyncpro.repository.billing.WebhookEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class WebhookProcessorTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private SubscriptionStatusMetrics subscriptionStatusMetrics;

    private WebhookProcessor processor;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        processor = new WebhookProcessor(
                webhookEventRepository,
                subscriptionRepository,
                invoiceRepository,
                new ObjectMapper(),
                subscriptionStatusMetrics,
                new SimpleMeterRegistry());
    }

    @Test
    void marksSubscriptionActiveOnPaymentSucceeded() {
        Subscription subscription = new Subscription();
        subscription.setStatus(SubscriptionStatus.PAST_DUE);

        WebhookEvent event = WebhookEvent.builder()
                .id(UUID.randomUUID())
                .provider("iyzico")
                .eventType("payment.succeeded")
                .payload("{\"subscriptionId\":\"sub-1\"}")
                .status(WebhookEventStatus.PENDING)
                .providerEventId("evt-1")
                .build();

        when(webhookEventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(subscriptionRepository.findByExternalSubscriptionId("sub-1"))
                .thenReturn(Optional.of(subscription));

        processor.process(event.getId());

        verify(subscriptionStatusMetrics).updateStatus(subscription, SubscriptionStatus.ACTIVE);
        verify(subscriptionRepository).save(subscription);
        assertThat(subscription.isCancelAtPeriodEnd()).isFalse();

        ArgumentCaptor<WebhookEvent> eventCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
    }
}
