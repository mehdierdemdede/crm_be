package com.leadsyncpro.billing.integration.iyzico;

import java.time.Instant;

public record IyzicoSubscriptionResponse(
        String subscriptionId,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd) {}
