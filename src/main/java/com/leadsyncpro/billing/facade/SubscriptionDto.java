package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.SubscriptionStatus;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionDto(
        UUID id,
        UUID customerId,
        String planCode,
        BillingPeriod billingPeriod,
        SubscriptionStatus status,
        Instant startAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant trialEndAt,
        boolean cancelAtPeriodEnd,
        int seatCount,
        String currency,
        String externalSubscriptionId) {}
