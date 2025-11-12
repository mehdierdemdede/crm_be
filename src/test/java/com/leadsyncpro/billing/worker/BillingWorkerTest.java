package com.leadsyncpro.billing.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leadsyncpro.billing.integration.iyzico.IyzicoClient;
import com.leadsyncpro.billing.integration.iyzico.IyzicoInvoiceResponse;
import com.leadsyncpro.billing.service.InvoicingService;
import com.leadsyncpro.billing.service.PricingService;
import com.leadsyncpro.billing.service.SubscriptionService;
import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.CustomerStatus;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.model.billing.SeatAllocation;
import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import com.leadsyncpro.repository.billing.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingWorkerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private IyzicoClient iyzicoClient;

    @Mock
    private BillingNotificationPort notificationPort;

    private final InvoicingService invoicingService = new InvoicingService(new PricingService());
    private final SubscriptionService subscriptionService = new SubscriptionService();
    private Clock clock;
    private BillingWorker billingWorker;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2024-03-10T03:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);
        billingWorker =
                new BillingWorker(subscriptionRepository, invoicingService, iyzicoClient, subscriptionService, notificationPort, clock);
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void runDunningCycleShouldActivateExpiredTrials() {
        Subscription trialSubscription = buildTrialSubscription(now.minus(2, ChronoUnit.DAYS));

        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL))
                .thenReturn(List.of(trialSubscription));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE))
                .thenReturn(Collections.emptyList());

        billingWorker.runDunningCycle();

        assertEquals(SubscriptionStatus.ACTIVE, trialSubscription.getStatus());
        assertEquals(trialSubscription.getTrialEndAt(), trialSubscription.getCurrentPeriodStart());
        verify(subscriptionRepository).save(trialSubscription);
        verify(notificationPort, never()).notifyPaymentRetrySuccess(any(), anyInt());
    }

    @Test
    void runDunningCycleShouldRetryPastDueAndActivateOnSuccess() {
        Subscription pastDue = buildPastDueSubscription(now.minus(1, ChronoUnit.DAYS));

        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE))
                .thenReturn(List.of(pastDue));
        when(iyzicoClient.createInvoice(anyString(), any(), any(), anyLong(), anyString()))
                .thenReturn(new IyzicoInvoiceResponse(
                        "inv_1", pastDue.getCurrentPeriodStart(), pastDue.getCurrentPeriodEnd(), 10_000L, pastDue.getPrice().getCurrency()));

        billingWorker.runDunningCycle();

        assertEquals(SubscriptionStatus.ACTIVE, pastDue.getStatus());
        verify(notificationPort).notifyPaymentRetrySuccess(pastDue, 1);
        verify(notificationPort, never()).notifyPaymentRetryFailure(any(), anyInt());
        verify(notificationPort, never()).notifySubscriptionCanceled(any());
    }

    @Test
    void runDunningCycleShouldCancelSubscriptionAfterFinalFailure() {
        Subscription pastDue = buildPastDueSubscription(now.minus(5, ChronoUnit.DAYS));

        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE))
                .thenReturn(List.of(pastDue));
        when(iyzicoClient.createInvoice(anyString(), any(), any(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("payment failed"));

        billingWorker.runDunningCycle();

        assertEquals(SubscriptionStatus.CANCELED, pastDue.getStatus());
        assertTrue(pastDue.isCancelAtPeriodEnd());
        verify(notificationPort).notifySubscriptionCanceled(pastDue);
        verify(notificationPort, never()).notifyPaymentRetrySuccess(any(), anyInt());
    }

    private Subscription buildTrialSubscription(Instant trialEnd) {
        Customer customer = Customer.builder()
                .externalId("cust-1")
                .email("user@example.com")
                .companyName("Example")
                .status(CustomerStatus.ACTIVE)
                .build();
        Plan plan = Plan.builder().code("basic").name("Basic").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(1_000L)
                .perSeatAmountCents(200L)
                .currency("USD")
                .build();
        Subscription subscription = Subscription.builder()
                .customer(customer)
                .plan(plan)
                .price(price)
                .status(SubscriptionStatus.TRIAL)
                .startAt(trialEnd.minus(14, ChronoUnit.DAYS))
                .currentPeriodStart(trialEnd.minus(14, ChronoUnit.DAYS))
                .trialEndAt(trialEnd)
                .cancelAtPeriodEnd(false)
                .seatAllocations(new ArrayList<>())
                .build();
        subscription.setUpdatedAt(trialEnd.minus(1, ChronoUnit.DAYS));
        subscription.getSeatAllocations()
                .add(SeatAllocation.builder()
                        .subscription(subscription)
                        .seatCount(5)
                        .effectiveFrom(subscription.getCurrentPeriodStart())
                        .build());
        return subscription;
    }

    private Subscription buildPastDueSubscription(Instant updatedAt) {
        Customer customer = Customer.builder()
                .externalId("cust-1")
                .email("user@example.com")
                .companyName("Example")
                .status(CustomerStatus.ACTIVE)
                .build();
        Plan plan = Plan.builder().code("pro").name("Pro").build();
        Price price = Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(2_000L)
                .perSeatAmountCents(500L)
                .currency("USD")
                .build();
        Subscription subscription = Subscription.builder()
                .customer(customer)
                .plan(plan)
                .price(price)
                .status(SubscriptionStatus.PAST_DUE)
                .startAt(updatedAt.minus(30, ChronoUnit.DAYS))
                .currentPeriodStart(updatedAt.minus(30, ChronoUnit.DAYS))
                .currentPeriodEnd(updatedAt.minus(30, ChronoUnit.DAYS).plus(30, ChronoUnit.DAYS))
                .cancelAtPeriodEnd(false)
                .externalSubscriptionId("ext-sub-1")
                .seatAllocations(new ArrayList<>())
                .build();
        subscription.setUpdatedAt(updatedAt);
        subscription.getSeatAllocations()
                .add(SeatAllocation.builder()
                        .subscription(subscription)
                        .seatCount(10)
                        .effectiveFrom(subscription.getCurrentPeriodStart())
                        .build());
        return subscription;
    }
}
