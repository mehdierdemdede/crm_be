package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.BillingPeriod;

public record ChangePlanCmd(String planCode, BillingPeriod billingPeriod, Proration proration) {

    public ChangePlanCmd {
        if (planCode == null || planCode.isBlank()) {
            throw new IllegalArgumentException("planCode must not be blank");
        }
        if (billingPeriod == null) {
            throw new IllegalArgumentException("billingPeriod must not be null");
        }
    }

    public Proration prorationOrDefault() {
        return proration != null ? proration : Proration.defaultValue();
    }
}
