package com.leadsyncpro.billing.service;

import com.leadsyncpro.model.billing.Customer;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.model.billing.SeatAllocation;
import com.leadsyncpro.model.billing.Subscription;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates domain operations for subscription lifecycle management.
 */
public class SubscriptionService {

    public Subscription create(
            Customer customer,
            Plan plan,
            Price price,
            int seatCount,
            Instant startAt,
            Instant trialEndAt) {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(startAt, "startAt must not be null");
        if (seatCount <= 0) {
            throw new IllegalArgumentException("Seat count must be greater than zero");
        }

        Subscription subscription = Subscription.builder()
                .customer(customer)
                .plan(plan)
                .price(price)
                .startAt(startAt)
                .currentPeriodStart(startAt)
                .trialEndAt(trialEndAt)
                .status(determineInitialStatus(startAt, trialEndAt))
                .cancelAtPeriodEnd(false)
                .build();

        attachSeatAllocation(subscription, seatCount, startAt);
        return subscription;
    }

    public void changePlan(Subscription subscription, Plan newPlan, Price newPrice, Instant effectiveFrom) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(newPlan, "newPlan must not be null");
        Objects.requireNonNull(newPrice, "newPrice must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        ensureNotCanceled(subscription);

        subscription.setPlan(newPlan);
        subscription.setPrice(newPrice);
        subscription.setCurrentPeriodStart(effectiveFrom);
        subscription.setCurrentPeriodEnd(null);

        if (subscription.getStatus() == SubscriptionStatus.TRIAL) {
            transitionStatus(subscription, SubscriptionStatus.ACTIVE);
        }
    }

    public void updateSeats(
            Subscription subscription, int seatCount, Instant effectiveFrom, boolean paymentCollected) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        ensureNotCanceled(subscription);
        if (seatCount <= 0) {
            throw new IllegalArgumentException("Seat count must be greater than zero");
        }

        attachSeatAllocation(subscription, seatCount, effectiveFrom);

        if (!paymentCollected) {
            transitionStatus(subscription, SubscriptionStatus.PAST_DUE);
        }
    }

    public void cancel(Subscription subscription, Instant cancellationEffectiveAt) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(cancellationEffectiveAt, "cancellationEffectiveAt must not be null");
        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException(
                    "Subscription must be in PAST_DUE state before it can be canceled");
        }

        subscription.setCurrentPeriodEnd(cancellationEffectiveAt);
        subscription.setCancelAtPeriodEnd(true);
        transitionStatus(subscription, SubscriptionStatus.CANCELED);
    }

    private void ensureNotCanceled(Subscription subscription) {
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("Subscription is already canceled");
        }
    }

    private void attachSeatAllocation(Subscription subscription, int seatCount, Instant effectiveFrom) {
        SeatAllocation allocation = SeatAllocation.builder()
                .subscription(subscription)
                .seatCount(seatCount)
                .effectiveFrom(effectiveFrom)
                .build();
        getSeatAllocations(subscription).add(allocation);
    }

    private List<SeatAllocation> getSeatAllocations(Subscription subscription) {
        List<SeatAllocation> allocations = subscription.getSeatAllocations();
        if (allocations == null) {
            allocations = new ArrayList<>();
            subscription.setSeatAllocations(allocations);
        }
        return allocations;
    }

    private void transitionStatus(Subscription subscription, SubscriptionStatus targetStatus) {
        SubscriptionStatus current = subscription.getStatus();
        if (current == targetStatus) {
            return;
        }
        if (!isTransitionAllowed(current, targetStatus)) {
            throw new IllegalStateException(
                    "Cannot transition subscription from " + current + " to " + targetStatus);
        }
        subscription.setStatus(targetStatus);
    }

    private boolean isTransitionAllowed(SubscriptionStatus current, SubscriptionStatus target) {
        if (current == null) {
            return target == SubscriptionStatus.TRIAL || target == SubscriptionStatus.ACTIVE;
        }
        return switch (current) {
            case TRIAL -> target == SubscriptionStatus.ACTIVE;
            case ACTIVE -> target == SubscriptionStatus.PAST_DUE;
            case PAST_DUE -> target == SubscriptionStatus.CANCELED;
            case CANCELED -> false;
        };
    }

    private SubscriptionStatus determineInitialStatus(Instant startAt, Instant trialEndAt) {
        if (trialEndAt != null && trialEndAt.isAfter(startAt)) {
            return SubscriptionStatus.TRIAL;
        }
        return SubscriptionStatus.ACTIVE;
    }
}
