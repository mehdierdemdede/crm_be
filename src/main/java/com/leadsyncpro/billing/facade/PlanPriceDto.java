package com.leadsyncpro.billing.facade;

import com.leadsyncpro.model.billing.BillingPeriod;
import java.math.BigDecimal;
import java.util.UUID;

public record PlanPriceDto(
        UUID id,
        BillingPeriod billingPeriod,
        BigDecimal amount,
        String currency,
        Integer seatLimit,
        Integer trialDays) {}
