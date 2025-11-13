package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.BillingPeriod;
import java.util.UUID;

public record PlanPriceDto(
        UUID id,
        BillingPeriod billingPeriod,
        Long baseAmountCents,
        Long perSeatAmountCents,
        String currency) {}
