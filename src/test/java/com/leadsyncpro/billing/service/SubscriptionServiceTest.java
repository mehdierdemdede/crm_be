package com.leadsyncpro.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.CustomerStatus;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.model.billing.SeatAllocation;
import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubscriptionServiceTest {

    private final SubscriptionService subscriptionService = new SubscriptionService();

    @Test
    void createShouldInitialiseTrialSubscription() {
        Customer customer = createCustomer();
        Plan plan = createPlan("basic");
        Price price = createPrice(plan, 1_000L, 200L);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant trialEnd = start.plus(14, ChronoUnit.DAYS);

        Subscription subscription =
                subscriptionService.create(customer, plan, price, 5, start, 14);

        assertSame(customer, subscription.getCustomer());
        assertSame(plan, subscription.getPlan());
        assertSame(price, subscription.getPrice());
        assertEquals(SubscriptionStatus.TRIAL, subscription.getStatus());
        assertEquals(start, subscription.getStartAt());
        assertEquals(start, subscription.getCurrentPeriodStart());
        assertEquals(trialEnd, subscription.getTrialEndAt());
        assertFalse(subscription.isCancelAtPeriodEnd());

        List<SeatAllocation> allocations = subscription.getSeatAllocations();
        assertEquals(1, allocations.size());
        SeatAllocation allocation = allocations.get(0);
        assertEquals(5, allocation.getSeatCount());
        assertEquals(start, allocation.getEffectiveFrom());
        assertSame(subscription, allocation.getSubscription());
    }

    @Test
    void changePlanShouldActivateSubscription() {
        Subscription subscription = createTrialSubscription();
        Plan proPlan = createPlan("pro");
        Price proPrice = createPrice(proPlan, 2_000L, 400L);
        Instant effectiveFrom = subscription.getStartAt().plus(15, ChronoUnit.DAYS);

        subscriptionService.changePlan(subscription, proPlan, proPrice, effectiveFrom);

        assertSame(proPlan, subscription.getPlan());
        assertSame(proPrice, subscription.getPrice());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals(effectiveFrom, subscription.getCurrentPeriodStart());
        assertNull(subscription.getCurrentPeriodEnd());
    }

    @Test
    void createShouldReturnActiveWhenTrialDaysMissing() {
        Customer customer = createCustomer();
        Plan plan = createPlan("basic");
        Price price = createPrice(plan, 1_000L, 200L);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");

        Subscription subscription = subscriptionService.create(customer, plan, price, 5, start, (Integer) null);

        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertNull(subscription.getTrialEndAt());
    }

    @Test
    void activateTrialShouldTransitionToActiveWhenTrialEnds() {
        Subscription subscription = createTrialSubscription();
        Instant trialEnd = subscription.getTrialEndAt();

        subscriptionService.activateTrial(subscription, trialEnd);

        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals(trialEnd, subscription.getCurrentPeriodStart());
    }

    @Test
    void activateTrialShouldRejectEarlyActivation() {
        Subscription subscription = createTrialSubscription();
        Instant beforeEnd = subscription.getTrialEndAt().minus(1, ChronoUnit.DAYS);

        assertThrows(
                IllegalStateException.class,
                () -> subscriptionService.activateTrial(subscription, beforeEnd));
    }

    @Test
    void updateSeatsShouldAddAllocationAndMarkPastDueWhenPaymentFails() {
        Subscription subscription = createActiveSubscription();
        Instant effectiveFrom = subscription.getCurrentPeriodStart().plus(30, ChronoUnit.DAYS);

        subscriptionService.updateSeats(subscription, 12, effectiveFrom, false);

        assertEquals(SubscriptionStatus.PAST_DUE, subscription.getStatus());
        List<SeatAllocation> allocations = subscription.getSeatAllocations();
        assertEquals(2, allocations.size());
        SeatAllocation latest = allocations.get(allocations.size() - 1);
        assertEquals(12, latest.getSeatCount());
        assertEquals(effectiveFrom, latest.getEffectiveFrom());
        assertSame(subscription, latest.getSubscription());
    }

    @Test
    void cancelShouldTransitionFromPastDueToCanceled() {
        Subscription subscription = createPastDueSubscription();
        Instant cancellationDate = subscription.getCurrentPeriodStart().plus(60, ChronoUnit.DAYS);

        subscriptionService.cancel(subscription, cancellationDate);

        assertEquals(SubscriptionStatus.CANCELED, subscription.getStatus());
        assertTrue(subscription.isCancelAtPeriodEnd());
        assertEquals(cancellationDate, subscription.getCurrentPeriodEnd());
    }

    @Test
    void cancelShouldRejectActiveSubscriptions() {
        Subscription subscription = createActiveSubscription();
        Instant cancellationDate = subscription.getCurrentPeriodStart().plus(10, ChronoUnit.DAYS);

        assertThrows(IllegalStateException.class, () -> subscriptionService.cancel(subscription, cancellationDate));
    }

    @Test
    void updateSeatsShouldRejectInvalidSeatCount() {
        Subscription subscription = createActiveSubscription();
        Instant effectiveFrom = subscription.getCurrentPeriodStart().plus(10, ChronoUnit.DAYS);

        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.updateSeats(subscription, 0, effectiveFrom, true));
    }

    @Test
    void changePlanShouldRejectCanceledSubscriptions() {
        Subscription subscription = createPastDueSubscription();
        Instant cancellationDate = subscription.getCurrentPeriodStart().plus(40, ChronoUnit.DAYS);
        subscriptionService.cancel(subscription, cancellationDate);

        Plan newPlan = createPlan("enterprise");
        Price newPrice = createPrice(newPlan, 5_000L, 1_000L);
        Instant effectiveFrom = cancellationDate.plus(1, ChronoUnit.DAYS);

        assertThrows(
                IllegalStateException.class,
                () -> subscriptionService.changePlan(subscription, newPlan, newPrice, effectiveFrom));
    }

    private Subscription createTrialSubscription() {
        Customer customer = createCustomer();
        Plan plan = createPlan("basic");
        Price price = createPrice(plan, 1_000L, 200L);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        return subscriptionService.create(customer, plan, price, 5, start, 14);
    }

    private Subscription createActiveSubscription() {
        Subscription subscription = createTrialSubscription();
        Plan proPlan = createPlan("pro");
        Price proPrice = createPrice(proPlan, 2_000L, 400L);
        Instant effectiveFrom = subscription.getStartAt().plus(14, ChronoUnit.DAYS);
        subscriptionService.changePlan(subscription, proPlan, proPrice, effectiveFrom);
        return subscription;
    }

    private Subscription createPastDueSubscription() {
        Subscription subscription = createActiveSubscription();
        Instant effectiveFrom = subscription.getCurrentPeriodStart().plus(30, ChronoUnit.DAYS);
        subscriptionService.updateSeats(subscription, 10, effectiveFrom, false);
        return subscription;
    }

    private Customer createCustomer() {
        return Customer.builder()
                .externalId("cust_123")
                .email("customer@example.com")
                .companyName("Example Corp")
                .status(CustomerStatus.ACTIVE)
                .build();
    }

    private Plan createPlan(String code) {
        return Plan.builder().code(code).name(code.toUpperCase()).build();
    }

    private Price createPrice(Plan plan, long baseAmount, long perSeatAmount) {
        return Price.builder()
                .plan(plan)
                .billingPeriod(BillingPeriod.MONTH)
                .baseAmountCents(baseAmount)
                .perSeatAmountCents(perSeatAmount)
                .currency("USD")
                .build();
    }
}
