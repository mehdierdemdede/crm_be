package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.BillingPeriod;
import java.util.UUID;

public record CreateSubscriptionCmd(
        UUID customerId,
        String planCode,
        BillingPeriod billingPeriod,
        int seatCount,
        Integer trialDays) {

    public CreateSubscriptionCmd {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }
        if (planCode == null || planCode.isBlank()) {
            throw new IllegalArgumentException("planCode must not be blank");
        }
        if (billingPeriod == null) {
            throw new IllegalArgumentException("billingPeriod must not be null");
        }
        if (seatCount <= 0) {
            throw new IllegalArgumentException("seatCount must be greater than zero");
        }
        if (trialDays != null && trialDays < 0) {
            throw new IllegalArgumentException("trialDays must not be negative");
        }
    }
}
